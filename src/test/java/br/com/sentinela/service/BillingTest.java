package br.com.sentinela.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.sentinela.dto.request.SubscribeRequestDto;
import br.com.sentinela.entity.Plan;
import br.com.sentinela.entity.Subscription;
import br.com.sentinela.entity.User;
import br.com.sentinela.enums.Feature;
import br.com.sentinela.infra.client.asaas.AsaasClient;
import br.com.sentinela.repository.PlanRepository;
import br.com.sentinela.repository.SubscriptionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class BillingTest {

    private static final String TOKEN = "token-de-webhook-com-mais-de-32-caracteres";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 10, 0);

    private final PlanRepository plans = mock(PlanRepository.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final PlanService planService = mock(PlanService.class);
    private final AsaasClient asaas = mock(AsaasClient.class);
    private final BillingService service = new BillingService(plans, subscriptions, planService, asaas, TOKEN);

    private final Plan normal = plan("NORMAL", "20.00", "200.00");
    private final Plan premium = plan("PREMIUM", "120.00", "1200.00");
    private final User user = User.builder().userId(UUID.randomUUID()).name("Ana").email("ana@example.com").build();
    private final Subscription subscription = Subscription.builder().userId(user.getUserId()).status("TRIAL")
            .trialEndsAt(LocalDateTime.now().minusDays(1)).asaasSubscriptionId("sub_1").build();

    @BeforeEach
    void setUp() {
        when(plans.findById("NORMAL")).thenReturn(Optional.of(normal));
        when(plans.findById("PREMIUM")).thenReturn(Optional.of(premium));
        when(subscriptions.lockByUserId(user.getUserId())).thenReturn(Optional.of(subscription));
        when(subscriptions.lockByAsaasSubscriptionId("sub_1")).thenReturn(Optional.of(subscription));
        when(subscriptions.registerEvent(anyString(), anyString(), any())).thenReturn(1);
        when(asaas.createCustomer(any(), any(), any(), any())).thenReturn("cus_1");
        when(asaas.createSubscription(any(), any(), any(), any(), any(), any(), any())).thenReturn("sub_2");
    }

    @Test
    void trialGivesPremiumUntilItEnds() {
        Subscription trial = Subscription.builder().status("TRIAL").trialEndsAt(NOW.plusSeconds(1)).build();
        assertThat(PlanService.resolve(trial, premium, NOW)).isSameAs(premium);

        trial.setTrialEndsAt(NOW);
        assertThat(PlanService.resolve(trial, premium, NOW)).isNull();
    }

    @Test
    void contractedPlanReplacesTheTrial() {
        Subscription chosen = Subscription.builder().status("PENDING").plan(normal).trialEndsAt(NOW.plusDays(5))
                .paidUntil(NOW.toLocalDate().plusDays(5)).build();

        assertThat(PlanService.resolve(chosen, premium, NOW)).isSameAs(normal);
    }

    @Test
    void accessLastsThreeDaysAfterThePaidPeriod() {
        Subscription paid = Subscription.builder().status("OVERDUE").plan(normal).trialEndsAt(NOW.minusDays(30))
                .paidUntil(NOW.toLocalDate().minusDays(3)).build();
        assertThat(PlanService.resolve(paid, premium, NOW)).isSameAs(normal);

        paid.setPaidUntil(NOW.toLocalDate().minusDays(4));
        assertThat(PlanService.resolve(paid, premium, NOW)).isNull();
    }

    @Test
    void canceledKeepsThePaidPeriodWithoutGrace() {
        Subscription canceled = Subscription.builder().status("CANCELED").plan(normal).trialEndsAt(NOW.minusDays(30))
                .paidUntil(NOW.toLocalDate()).build();
        assertThat(PlanService.resolve(canceled, premium, NOW)).isSameAs(normal);

        canceled.setPaidUntil(NOW.toLocalDate().minusDays(1));
        assertThat(PlanService.resolve(canceled, premium, NOW)).isNull();
    }

    @Test
    void firstChargeWaitsForTheTrialAndForThePaidPeriod() {
        LocalDate today = LocalDate.of(2026, 11, 5);
        assertThat(BillingService.nextDueDate(today, null, null)).isEqualTo(today);
        assertThat(BillingService.nextDueDate(today, today.plusDays(4), null)).isEqualTo(today.plusDays(4));
        assertThat(BillingService.nextDueDate(today, today.minusDays(1), today.plusDays(20))).isEqualTo(today.plusDays(20));
    }

    @Test
    void valueComesFromTheDatabaseAndOldRecurrenceIsRemovedFirst() {
        var response = service.subscribe(user, new SubscribeRequestDto("PREMIUM", "YEARLY", "123.456.789-09", "PIX"));

        assertThat(response.value()).isEqualByComparingTo("1200.00");
        var order = inOrder(asaas);
        order.verify(asaas).createCustomer("Ana", "ana@example.com", "12345678909", user.getUserId().toString());
        order.verify(asaas).deleteActiveSubscriptions(user.getUserId().toString());
        order.verify(asaas).createSubscription(eq("cus_1"), eq(new BigDecimal("1200.00")), eq("YEARLY"), any(), any(), any(), eq("PIX"));
        assertThat(subscription.getStatus()).isEqualTo("PENDING");
        assertThat(subscription.getAsaasSubscriptionId()).isEqualTo("sub_2");
    }

    @Test
    void changingPlanWhilePaidWaitsForTheNextPayment() {
        LocalDate paidUntil = LocalDate.now().plusDays(10);
        subscription.setStatus("ACTIVE");
        subscription.setPlan(normal);
        subscription.setCycle("MONTHLY");
        subscription.setPaidUntil(paidUntil);
        subscription.setAsaasCustomerId("cus_1");

        var response = service.subscribe(user, new SubscribeRequestDto("PREMIUM", "MONTHLY", null, "PIX"));

        assertThat(response.nextDueDate()).isEqualTo(paidUntil);
        assertThat(subscription.getPlan()).isSameAs(normal);
        assertThat(subscription.getPendingPlan()).isSameAs(premium);
        verify(asaas, never()).createCustomer(any(), any(), any(), any());
    }

    @Test
    void subscribingAgainAfterCancelWaitsForThePaidPeriodToEnd() {
        LocalDate paidUntil = LocalDate.now().plusDays(10);
        subscription.setStatus("CANCELED");
        subscription.setPlan(normal);
        subscription.setCycle("MONTHLY");
        subscription.setPaidUntil(paidUntil);
        subscription.setAsaasCustomerId("cus_1");

        var response = service.subscribe(user, new SubscribeRequestDto("NORMAL", "MONTHLY", null, "PIX"));

        assertThat(response.nextDueDate()).isEqualTo(paidUntil);
        assertThat(subscription.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void cardGoesToAsaasCheckoutWithoutDocumentAndKeepsCurrentRecurrence() {
        subscription.setStatus("ACTIVE");
        subscription.setPlan(normal);
        subscription.setPaidUntil(LocalDate.now().plusDays(10));
        when(asaas.createCheckout(any(), any(), any(), any(), any())).thenReturn(new AsaasClient.Checkout("chk_1", "https://checkout"));

        var response = service.subscribe(user, new SubscribeRequestDto("PREMIUM", "MONTHLY", null, "CARD"));

        assertThat(response.invoiceUrl()).isEqualTo("https://checkout");
        assertThat(subscription.getCheckoutId()).isEqualTo("chk_1");
        assertThat(subscription.getPlan()).isSameAs(normal);
        assertThat(subscription.getPendingPlan()).isNull();
        assertThat(subscription.getAsaasSubscriptionId()).isEqualTo("sub_1");
        verify(asaas, never()).deleteActiveSubscriptions(any());
        verify(asaas, never()).createCustomer(any(), any(), any(), any());
    }

    @Test
    void checkoutConclusionLinksTheSubscriptionAndRemovesTheOldOne() {
        subscription.setCheckoutId("chk_1");
        subscription.setCheckoutPlan(premium);
        subscription.setCheckoutCycle("YEARLY");
        when(subscriptions.lockByCheckoutId("chk_1")).thenReturn(Optional.of(subscription));
        JsonNode event = JsonMapper.builder().build().readTree("""
                {"id":"evt_1","event":"SUBSCRIPTION_CREATED","subscription":{"id":"sub_2","customer":"cus_9",
                 "nextDueDate":"2026-12-05T03:00:00Z","checkoutSession":"chk_1"}}""");

        service.handleWebhook(TOKEN, event);

        verify(asaas).deleteSubscription("sub_1");
        assertThat(subscription.getAsaasSubscriptionId()).isEqualTo("sub_2");
        assertThat(subscription.getAsaasCustomerId()).isEqualTo("cus_9");
        assertThat(subscription.getPlan()).isSameAs(premium);
        assertThat(subscription.getStatus()).isEqualTo("PENDING");
        assertThat(subscription.getCheckoutId()).isNull();
    }

    @Test
    void paymentBeforeSubscriptionCreatedStillLinksTheCheckout() {
        subscription.setAsaasSubscriptionId(null);
        subscription.setCheckoutId("chk_1");
        subscription.setCheckoutPlan(premium);
        subscription.setCheckoutCycle("MONTHLY");
        when(subscriptions.lockByAsaasSubscriptionId("sub_2")).thenReturn(Optional.empty());
        when(subscriptions.lockByCheckoutId("chk_1")).thenReturn(Optional.of(subscription));
        JsonNode event = JsonMapper.builder().build().readTree("""
                {"id":"evt_1","event":"PAYMENT_CONFIRMED","payment":{"subscription":"sub_2","customer":"cus_9",
                 "checkoutSession":"chk_1","dueDate":"2026-11-05"}}""");

        service.handleWebhook(TOKEN, event);

        assertThat(subscription.getStatus()).isEqualTo("ACTIVE");
        assertThat(subscription.getPlan()).isSameAs(premium);
        assertThat(subscription.getPaidUntil()).isEqualTo(LocalDate.of(2026, 12, 5));
    }

    @Test
    void subscriptionWithoutCheckoutIsIgnoredByTheLinkStep() {
        service.handleWebhook(TOKEN, JsonMapper.builder().build().readTree(
                "{\"id\":\"evt_1\",\"event\":\"SUBSCRIPTION_CREATED\",\"subscription\":{\"id\":\"sub_2\",\"checkoutSession\":null}}"));

        verify(subscriptions, never()).lockByCheckoutId(any());
    }

    @Test
    void newCustomerNeedsAValidDocument() {
        assertThatThrownBy(() -> service.subscribe(user, new SubscribeRequestDto("NORMAL", "MONTHLY", null, "PIX")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(asaas, never()).createSubscription(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void inactivePlanCannotBeContracted() {
        normal.setActive(false);

        assertThatThrownBy(() -> service.subscribe(user, new SubscribeRequestDto("NORMAL", "MONTHLY", "12345678909", "PIX")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void webhookWithoutTheRightTokenChangesNothing() {
        JsonNode event = event("evt_1", "PAYMENT_CONFIRMED", "sub_1", "2026-11-05");

        for (String token : new String[]{null, "", "errado"}) {
            assertThatThrownBy(() -> service.handleWebhook(token, event)).isInstanceOfSatisfying(ResponseStatusException.class,
                    exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        }
        verify(subscriptions, never()).registerEvent(any(), any(), any());
    }

    @Test
    void webhookIsRejectedWhenNoTokenIsConfigured() {
        BillingService unconfigured = new BillingService(plans, subscriptions, planService, asaas, "");

        assertThatThrownBy(() -> unconfigured.handleWebhook("", event("evt_1", "PAYMENT_CONFIRMED", "sub_1", "2026-11-05")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void confirmedPaymentReleasesThePeriodAndAppliesThePendingPlan() {
        subscription.setStatus("PENDING");
        subscription.setPlan(normal);
        subscription.setCycle("MONTHLY");
        subscription.setPendingPlan(premium);
        subscription.setPendingCycle("YEARLY");

        service.handleWebhook(TOKEN, event("evt_1", "PAYMENT_CONFIRMED", "sub_1", "2026-11-05"));

        assertThat(subscription.getStatus()).isEqualTo("ACTIVE");
        assertThat(subscription.getPlan()).isSameAs(premium);
        assertThat(subscription.getPendingPlan()).isNull();
        assertThat(subscription.getPaidUntil()).isEqualTo(LocalDate.of(2027, 11, 5));
    }

    @Test
    void confirmedAndReceivedForTheSamePaymentDoNotExtendTwice() {
        subscription.setCycle("MONTHLY");

        service.handleWebhook(TOKEN, event("evt_1", "PAYMENT_CONFIRMED", "sub_1", "2026-11-05"));
        service.handleWebhook(TOKEN, event("evt_2", "PAYMENT_RECEIVED", "sub_1", "2026-11-05"));

        assertThat(subscription.getPaidUntil()).isEqualTo(LocalDate.of(2026, 12, 5));
    }

    @Test
    void repeatedEventIsIgnored() {
        when(subscriptions.registerEvent(eq("evt_1"), anyString(), any())).thenReturn(0);

        service.handleWebhook(TOKEN, event("evt_1", "PAYMENT_CONFIRMED", "sub_1", "2026-11-05"));

        verify(subscriptions, never()).lockByAsaasSubscriptionId(any());
    }

    @Test
    void refundRemovesThePaidPeriod() {
        subscription.setStatus("ACTIVE");
        subscription.setPaidUntil(LocalDate.of(2026, 12, 5));

        service.handleWebhook(TOKEN, event("evt_1", "PAYMENT_REFUNDED", "sub_1", "2026-11-05"));

        assertThat(subscription.getPaidUntil()).isNull();
        assertThat(subscription.getStatus()).isEqualTo("OVERDUE");
    }

    @Test
    void eventFromReplacedRecurrenceDoesNotCancelTheCurrentOne() {
        subscription.setStatus("ACTIVE");
        when(subscriptions.lockByAsaasSubscriptionId("sub_antiga")).thenReturn(Optional.empty());
        JsonNode event = JsonMapper.builder().build().readTree(
                "{\"id\":\"evt_9\",\"event\":\"SUBSCRIPTION_DELETED\",\"subscription\":{\"id\":\"sub_antiga\"}}");

        service.handleWebhook(TOKEN, event);

        assertThat(subscription.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void cancelRemovesTheRecurrenceAndKeepsThePaidPeriod() {
        LocalDate paidUntil = LocalDate.now().plusDays(10);
        subscription.setStatus("ACTIVE");
        subscription.setPaidUntil(paidUntil);

        service.cancel(user.getUserId());

        verify(asaas).deleteActiveSubscriptions(user.getUserId().toString());
        assertThat(subscription.getStatus()).isEqualTo("CANCELED");
        assertThat(subscription.getPaidUntil()).isEqualTo(paidUntil);
    }

    private JsonNode event(String id, String type, String subscriptionId, String dueDate) {
        return JsonMapper.builder().build().readTree("""
                {"id":"%s","event":"%s","payment":{"id":"pay_1","subscription":"%s","dueDate":"%s"}}"""
                .formatted(id, type, subscriptionId, dueDate));
    }

    private Plan plan(String code, String monthly, String yearly) {
        return Plan.builder().code(code).name(code).monthlyPrice(new BigDecimal(monthly)).yearlyPrice(new BigDecimal(yearly))
                .active(true).features(Set.of(Feature.BASE)).build();
    }
}
