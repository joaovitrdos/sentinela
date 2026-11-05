package br.com.sentinela.service;

import br.com.sentinela.dto.request.SubscribeRequestDto;
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
import java.util.UUID;

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

    /**
     * Assinar, trocar de plano e voltar a assinar seguem o mesmo caminho: a recorrência antiga é removida e a nova
     * começa no fim do período já pago, sem cobrança dupla. O bloqueio impede duas assinaturas por clique duplo.
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
        asaas.deleteActiveSubscriptions(reference);

        LocalDate today = LocalDate.now(ZONE);
        boolean inTrial = LocalDateTime.now().isBefore(subscription.getTrialEndsAt());
        boolean paidPeriod = ("ACTIVE".equals(subscription.getStatus()) || "CANCELED".equals(subscription.getStatus()))
                && subscription.getPaidUntil() != null && !subscription.getPaidUntil().isBefore(today);
        LocalDate nextDueDate = nextDueDate(today, inTrial ? subscription.getTrialEndsAt().toLocalDate() : null,
                paidPeriod ? subscription.getPaidUntil() : null);
        String asaasId = asaas.createSubscription(subscription.getAsaasCustomerId(), value, request.cycle(), nextDueDate,
                "Sentinela - Plano " + plan.getName(), reference);

        subscription.setAsaasSubscriptionId(asaasId);
        if (paidPeriod) {
            subscription.setPendingPlan(plan);
            subscription.setPendingCycle(request.cycle());
            subscription.setStatus("ACTIVE");
        } else {
            subscription.setPlan(plan);
            subscription.setCycle(request.cycle());
            subscription.setPendingPlan(null);
            subscription.setPendingCycle(null);
            subscription.setStatus("PENDING");
            if (inTrial) subscription.setPaidUntil(nextDueDate);
        }
        subscriptions.save(subscription);
        return new SubscribeResponseDto(plan.getCode(), request.cycle(), value, nextDueDate, asaas.firstInvoiceUrl(asaasId));
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

    /** O período já pago continua valendo até o fim, sem os dias de tolerância. */
    @Transactional
    public void cancel(UUID userId) {
        planService.subscriptionOf(userId);
        Subscription subscription = subscriptions.lockByUserId(userId).orElseThrow();
        if (subscription.getAsaasSubscriptionId() == null || "CANCELED".equals(subscription.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nenhuma assinatura ativa para cancelar");
        }
        asaas.deleteActiveSubscriptions(userId.toString());
        subscription.setStatus("CANCELED");
        subscription.setPendingPlan(null);
        subscription.setPendingCycle(null);
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
        switch (type) {
            case "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED" -> forSubscription(payment.path("subscription").asText(""), subscription ->
                    applyPayment(subscription, LocalDate.parse(payment.path("dueDate").asText())));
            case "PAYMENT_OVERDUE" -> forSubscription(payment.path("subscription").asText(""), subscription -> {
                if (!"CANCELED".equals(subscription.getStatus())) subscription.setStatus("OVERDUE");
            });
            case "PAYMENT_REFUNDED", "PAYMENT_CHARGEBACK_REQUESTED" -> forSubscription(payment.path("subscription").asText(""), subscription -> {
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
    private void forSubscription(String asaasId, java.util.function.Consumer<Subscription> change) {
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
