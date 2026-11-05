package br.com.sentinela.service;

import br.com.sentinela.dto.request.SubscribeRequestDto;
import br.com.sentinela.dto.response.BillingStatusResponseDto;
import br.com.sentinela.dto.response.SubscribeResponseDto;
import br.com.sentinela.entity.Plan;
import br.com.sentinela.entity.Subscription;
import br.com.sentinela.entity.User;
import br.com.sentinela.infra.client.asaas.AsaasClient;
import br.com.sentinela.repository.PlanRepository;
import br.com.sentinela.repository.SubscriptionRepository;
import br.com.sentinela.util.LogUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class BillingService {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final PlanService planService;
    private final AsaasClient asaas;
    private final String webhookToken;

    public BillingService(PlanRepository plans, SubscriptionRepository subscriptions, PlanService planService, AsaasClient asaas,
                          @Value("${asaas.webhook-token:}") String webhookToken) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.planService = planService;
        this.asaas = asaas;
        this.webhookToken = webhookToken;
    }

    /** Confere o pagamento no Asaas antes de responder: a tela não pode depender só da chegada do webhook. */
    @Transactional
    public BillingStatusResponseDto status(UUID userId) {
        planService.subscriptionOf(userId);
        Subscription subscription = subscriptions.lockByUserId(userId).orElseThrow();
        if ("PENDING".equals(subscription.getStatus()) || "OVERDUE".equals(subscription.getStatus())) {
            reconcile(subscription, userId.toString());
        }
        return planService.status(userId);
    }

    private void reconcile(Subscription subscription, String reference) {
        try {
            for (String asaasId : asaas.activeSubscriptionIds(reference)) {
                List<LocalDate> paid = asaas.paidDueDates(asaasId);
                if (paid.isEmpty()) continue;
                subscription.setAsaasSubscriptionId(asaasId);
                paid.forEach(dueDate -> applyPayment(subscription, dueDate));
                subscriptions.save(subscription);
                return;
            }
        } catch (ResponseStatusException unavailable) {
            LogUtil.warn("Não foi possível conferir o pagamento no Asaas");
        }
    }

    /**
     * Assinar, trocar de plano e voltar a assinar seguem o mesmo caminho: a recorrência antiga é removida e a nova começa
     * no fim do período já pago, sem cobrança dupla. A forma de pagamento é escolhida pelo cliente na fatura do Asaas.
     */
    @Transactional
    public SubscribeResponseDto subscribe(User user, SubscribeRequestDto request) {
        Plan plan = plans.findById(request.planCode()).filter(Plan::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plano não encontrado"));
        BigDecimal value = "YEARLY".equals(request.cycle()) ? plan.getYearlyPrice() : plan.getMonthlyPrice();
        planService.subscriptionOf(user.getUserId());
        Subscription subscription = subscriptions.lockByUserId(user.getUserId()).orElseThrow();
        String reference = user.getUserId().toString();

        if (subscription.getAsaasCustomerId() == null) {
            subscription.setAsaasCustomerId(customer(user, request.cpfCnpj(), reference));
        }
        removeRecurrence(subscription, reference);

        LocalDate today = LocalDate.now(ZONE);
        boolean paidPeriod = inPaidPeriod(subscription, today);
        LocalDate nextDueDate = nextDueDate(today, inTrial(subscription) ? subscription.getTrialEndsAt().toLocalDate() : null,
                paidPeriod ? subscription.getPaidUntil() : null);
        String asaasId = asaas.createSubscription(subscription.getAsaasCustomerId(), value, request.cycle(), nextDueDate,
                "Sentinela - Plano " + plan.getName(), reference);
        boolean samePlanAlreadyPaid = paidPeriod && plan.getCode().equals(subscription.getPlan().getCode())
                && request.cycle().equals(subscription.getCycle());

        subscription.setAsaasSubscriptionId(asaasId);
        if (samePlanAlreadyPaid) {
            subscription.setPendingPlan(null);
            subscription.setPendingCycle(null);
            subscription.setStatus("ACTIVE");
        } else if (paidPeriod) {
            subscription.setPendingPlan(plan);
            subscription.setPendingCycle(request.cycle());
            subscription.setStatus("ACTIVE");
        } else {
            subscription.setPlan(plan);
            subscription.setCycle(request.cycle());
            subscription.setPendingPlan(null);
            subscription.setPendingCycle(null);
            subscription.setStatus("PENDING");
        }
        subscriptions.save(subscription);
        // Reativar o mesmo plano não gera pagamento agora: a próxima cobrança vence no fim do período já pago.
        String invoiceUrl = samePlanAlreadyPaid ? null : asaas.firstInvoiceUrl(asaasId);
        return new SubscribeResponseDto(plan.getCode(), request.cycle(), value, nextDueDate, invoiceUrl);
    }

    private boolean inTrial(Subscription subscription) {
        return LocalDateTime.now().isBefore(subscription.getTrialEndsAt());
    }

    private boolean inPaidPeriod(Subscription subscription, LocalDate today) {
        return subscription.getPlan() != null && subscription.getPaidUntil() != null && !subscription.getPaidUntil().isBefore(today);
    }

    private void removeRecurrence(Subscription subscription, String reference) {
        String current = subscription.getAsaasSubscriptionId();
        if (current != null) {
            try {
                asaas.deleteSubscription(current);
            } catch (ResponseStatusException alreadyRemoved) {
                LogUtil.warn("Assinatura anterior do Asaas já não estava disponível");
            }
        }
        asaas.deleteActiveSubscriptions(reference);
    }

    private String customer(User user, String rawDocument, String reference) {
        String existing = asaas.findCustomerId(reference);
        if (existing != null) return existing;
        String document = rawDocument == null ? "" : rawDocument.replaceAll("\\D", "");
        if (document.length() != 11 && document.length() != 14) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um CPF ou CNPJ válido");
        }
        return asaas.createCustomer(user.getName(), user.getEmail(), document, reference);
    }

    static LocalDate nextDueDate(LocalDate today, LocalDate trialEnd, LocalDate paidUntil) {
        LocalDate date = today;
        if (trialEnd != null && trialEnd.isAfter(date)) date = trialEnd;
        if (paidUntil != null && paidUntil.isAfter(date)) date = paidUntil;
        return date;
    }

    /** O período pago continua valendo até o fim. Sem pagamento, é como se a assinatura nunca tivesse existido. */
    @Transactional
    public void cancel(UUID userId) {
        planService.subscriptionOf(userId);
        Subscription subscription = subscriptions.lockByUserId(userId).orElseThrow();
        if (subscription.getAsaasSubscriptionId() == null || "CANCELED".equals(subscription.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nenhuma assinatura ativa para cancelar");
        }
        removeRecurrence(subscription, userId.toString());
        subscription.setPendingPlan(null);
        subscription.setPendingCycle(null);
        if (subscription.getPaidUntil() == null) {
            subscription.setPlan(null);
            subscription.setCycle(null);
            subscription.setAsaasSubscriptionId(null);
            subscription.setStatus(inTrial(subscription) ? "TRIAL" : "CANCELED");
        } else {
            subscription.setStatus("CANCELED");
        }
        subscriptions.save(subscription);
    }

    /** Evento e efeito ficam na mesma transação: se falhar, o Asaas reenvia e nada fica pela metade. */
    @Transactional
    public void handleWebhook(String token, JsonNode event) {
        if (!validToken(token)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido");
        String id = event.path("id").asText("");
        String type = event.path("event").asText("");
        if (id.isBlank() || type.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evento inválido");
        if (subscriptions.registerEvent(id, type, LocalDateTime.now()) == 0) return;

        JsonNode payment = event.path("payment");
        String paymentSubscription = payment.path("subscription").asText("");
        switch (type) {
            case "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED" -> forSubscription(paymentSubscription, subscription ->
                    applyPayment(subscription, LocalDate.parse(payment.path("dueDate").asText().substring(0, 10))));
            case "PAYMENT_OVERDUE" -> forSubscription(paymentSubscription, subscription -> {
                if (!"CANCELED".equals(subscription.getStatus())) subscription.setStatus("OVERDUE");
            });
            case "PAYMENT_REFUNDED", "PAYMENT_CHARGEBACK_REQUESTED" -> forSubscription(paymentSubscription, subscription -> {
                subscription.setPaidUntil(null);
                if (!"CANCELED".equals(subscription.getStatus())) subscription.setStatus("OVERDUE");
            });
            case "SUBSCRIPTION_DELETED", "SUBSCRIPTION_INACTIVATED" -> forSubscription(event.path("subscription").path("id").asText(""),
                    subscription -> subscription.setStatus("CANCELED"));
            default -> { }
        }
    }

    private boolean validToken(String token) {
        return !webhookToken.isBlank() && token != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), webhookToken.getBytes(StandardCharsets.UTF_8));
    }

    /** Eventos de uma recorrência já substituída não encontram registro e são ignorados de propósito. */
    private void forSubscription(String asaasId, Consumer<Subscription> change) {
        subscriptions.lockByAsaasSubscriptionId(asaasId).ifPresentOrElse(subscription -> {
            change.accept(subscription);
            subscriptions.save(subscription);
        }, () -> LogUtil.warn("Evento do Asaas sem assinatura vigente correspondente"));
    }

    static void applyPayment(Subscription subscription, LocalDate dueDate) {
        if (subscription.getPendingPlan() != null) {
            subscription.setPlan(subscription.getPendingPlan());
            subscription.setCycle(subscription.getPendingCycle());
            subscription.setPendingPlan(null);
            subscription.setPendingCycle(null);
        }
        LocalDate end = "YEARLY".equals(subscription.getCycle()) ? dueDate.plusYears(1) : dueDate.plusMonths(1);
        if (subscription.getPaidUntil() == null || end.isAfter(subscription.getPaidUntil())) subscription.setPaidUntil(end);
        if (!"CANCELED".equals(subscription.getStatus())) subscription.setStatus("ACTIVE");
    }
}
