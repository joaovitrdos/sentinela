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
     * Cartão vai para o Checkout do Asaas; Pix e boleto, para a fatura. No Checkout a escolha fica guardada à parte até o
     * Asaas criar a assinatura, para que desistir do pagamento não mexa no plano atual. O bloqueio evita o clique duplo.
     */
    @Transactional
    public SubscribeResponseDto subscribe(User user, SubscribeRequestDto request) {
        Plan plan = plans.findById(request.planCode()).filter(Plan::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plano não encontrado"));
        BigDecimal value = "YEARLY".equals(request.cycle()) ? plan.getYearlyPrice() : plan.getMonthlyPrice();
        planService.subscriptionOf(user.getUserId());
        Subscription subscription = subscriptions.lockByUserId(user.getUserId()).orElseThrow();
        String reference = user.getUserId().toString();
        LocalDate today = LocalDate.now(ZONE);
        LocalDate nextDueDate = nextDueDate(today, inTrial(subscription) ? subscription.getTrialEndsAt().toLocalDate() : null,
                inPaidPeriod(subscription, today) ? subscription.getPaidUntil() : null);

        if ("CARD".equals(request.paymentMethod())) {
            AsaasClient.Checkout checkout = asaas.createCheckout(plan.getName(), value, request.cycle(), nextDueDate, reference);
            subscription.setCheckoutId(checkout.id());
            subscription.setCheckoutPlan(plan);
            subscription.setCheckoutCycle(request.cycle());
            subscriptions.save(subscription);
            return new SubscribeResponseDto(plan.getCode(), request.cycle(), value, nextDueDate, checkout.link());
        }

        if (subscription.getAsaasCustomerId() == null) {
            subscription.setAsaasCustomerId(customer(user, request.cpfCnpj(), reference));
        }
        removeRecurrence(subscription, reference);
        String asaasId = asaas.createSubscription(subscription.getAsaasCustomerId(), value, request.cycle(), nextDueDate,
                "Sentinela - Plano " + plan.getName(), reference, request.paymentMethod());
        subscription.setAsaasSubscriptionId(asaasId);
        clearCheckout(subscription);
        applySelection(subscription, plan, request.cycle(), nextDueDate, today);
        subscriptions.save(subscription);
        return new SubscribeResponseDto(plan.getCode(), request.cycle(), value, nextDueDate, asaas.firstInvoiceUrl(asaasId));
    }

    private boolean inTrial(Subscription subscription) {
        return LocalDateTime.now().isBefore(subscription.getTrialEndsAt());
    }

    private boolean inPaidPeriod(Subscription subscription, LocalDate today) {
        return ("ACTIVE".equals(subscription.getStatus()) || "CANCELED".equals(subscription.getStatus()))
                && subscription.getPaidUntil() != null && !subscription.getPaidUntil().isBefore(today);
    }

    private void applySelection(Subscription subscription, Plan plan, String cycle, LocalDate nextDueDate, LocalDate today) {
        if (inPaidPeriod(subscription, today)) {
            subscription.setPendingPlan(plan);
            subscription.setPendingCycle(cycle);
            subscription.setStatus("ACTIVE");
            return;
        }
        subscription.setPlan(plan);
        subscription.setCycle(cycle);
        subscription.setPendingPlan(null);
        subscription.setPendingCycle(null);
        subscription.setStatus("PENDING");
        if (inTrial(subscription)) subscription.setPaidUntil(nextDueDate);
    }

    private void clearCheckout(Subscription subscription) {
        subscription.setCheckoutId(null);
        subscription.setCheckoutPlan(null);
        subscription.setCheckoutCycle(null);
    }

    /** A assinatura do Checkout pode não trazer o identificador do Sentinela, por isso a atual é removida pelo id. */
    private void removeRecurrence(Subscription subscription, String reference) {
        deleteQuietly(subscription.getAsaasSubscriptionId());
        asaas.deleteActiveSubscriptions(reference);
    }

    private void deleteQuietly(String asaasSubscriptionId) {
        if (asaasSubscriptionId == null) return;
        try {
            asaas.deleteSubscription(asaasSubscriptionId);
        } catch (ResponseStatusException alreadyRemoved) {
            LogUtil.warn("Assinatura anterior do Asaas já não estava disponível");
        }
    }

    /** Liga a assinatura criada pelo Checkout ao usuário, pelo campo checkoutSession enviado pelo Asaas. */
    private Subscription linkCheckout(Subscription subscription, String asaasId, String customerId, LocalDate nextDueDate) {
        if (!asaasId.equals(subscription.getAsaasSubscriptionId())) deleteQuietly(subscription.getAsaasSubscriptionId());
        subscription.setAsaasSubscriptionId(asaasId);
        if (!customerId.isBlank()) subscription.setAsaasCustomerId(customerId);
        applySelection(subscription, subscription.getCheckoutPlan(), subscription.getCheckoutCycle(), nextDueDate, LocalDate.now(ZONE));
        clearCheckout(subscription);
        return subscription;
    }

    private LocalDate date(JsonNode value) {
        return LocalDate.parse(value.asText().substring(0, 10));
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
        removeRecurrence(subscription, userId.toString());
        clearCheckout(subscription);
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
            case "SUBSCRIPTION_CREATED" -> {
                JsonNode created = event.path("subscription");
                String checkoutSession = created.path("checkoutSession").asText("");
                if (!checkoutSession.isBlank()) {
                    subscriptions.lockByCheckoutId(checkoutSession).ifPresent(subscription -> subscriptions.save(linkCheckout(subscription,
                            created.path("id").asText(), created.path("customer").asText(""), date(created.path("nextDueDate")))));
                }
            }
            case "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED" -> forPayment(payment, subscription -> applyPayment(subscription, date(payment.path("dueDate"))));
            case "PAYMENT_OVERDUE" -> forPayment(payment, subscription -> {
                if (!"CANCELED".equals(subscription.getStatus())) subscription.setStatus("OVERDUE");
            });
            case "PAYMENT_REFUNDED", "PAYMENT_CHARGEBACK_REQUESTED" -> forPayment(payment, subscription -> {
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

    /** O pagamento pode chegar antes do SUBSCRIPTION_CREATED: nesse caso a ligação com o Checkout é feita aqui. */
    private void forPayment(JsonNode payment, java.util.function.Consumer<Subscription> change) {
        String asaasId = payment.path("subscription").asText("");
        String checkoutSession = payment.path("checkoutSession").asText("");
        var linked = subscriptions.lockByAsaasSubscriptionId(asaasId);
        if (linked.isEmpty() && !checkoutSession.isBlank()) {
            linked = subscriptions.lockByCheckoutId(checkoutSession).map(subscription ->
                    linkCheckout(subscription, asaasId, payment.path("customer").asText(""), date(payment.path("dueDate"))));
        }
        linked.ifPresentOrElse(subscription -> {
            change.accept(subscription);
            subscriptions.save(subscription);
        }, () -> LogUtil.warn("Evento do Asaas sem assinatura vigente correspondente"));
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
