package br.com.sentinela.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.sentinela.entity.Plan;
import br.com.sentinela.entity.Property;
import br.com.sentinela.entity.Subscription;
import br.com.sentinela.entity.User;
import br.com.sentinela.enums.Feature;
import br.com.sentinela.repository.PlanRepository;
import br.com.sentinela.repository.PropertyMemberRepository;
import br.com.sentinela.repository.PropertyRepository;
import br.com.sentinela.repository.SubscriptionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;

/** Regras comerciais de cada plano, tal como cadastradas na migration V12. */
class PlanRulesTest {

    private static final Set<Feature> MEDIO_FEATURES = EnumSet.of(Feature.BASE, Feature.REPORTS, Feature.SPRAY_WINDOW,
            Feature.NDVI, Feature.ALERTS, Feature.TEAM);

    private final Plan normal = plan("NORMAL", 1, EnumSet.of(Feature.BASE));
    private final Plan medio = plan("MEDIO", 5, MEDIO_FEATURES);
    private final Plan premium = plan("PREMIUM", null, EnumSet.allOf(Feature.class));

    private final PlanRepository plans = mock(PlanRepository.class);
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final PropertyMemberRepository members = mock(PropertyMemberRepository.class);
    private final PlanService service = new PlanService(plans, subscriptions, properties, members);

    private final UUID ownerId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final UUID firstId = UUID.randomUUID();
    private final UUID secondId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(plans.findById("PREMIUM")).thenReturn(Optional.of(premium));
        property(firstId, false);
        property(secondId, false);
        when(properties.findOwnedIdsOldestFirst(ownerId)).thenReturn(List.of(firstId, secondId));
    }

    @Test
    void choosingNormalDuringTrialAppliesNormalRulesImmediately() {
        contracted(normal);
        when(properties.countByUser_UserId(ownerId)).thenReturn(1L);

        assertThatThrownBy(() -> service.requirePropertySlot(ownerId)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Seu plano permite até 1 propriedade(s)");
        assertThat(service.allows(ownerId, Feature.REPORTS)).isFalse();
    }

    @Test
    void trialWithoutContractedPlanHasEverything() {
        subscription(Subscription.builder().status("TRIAL").trialEndsAt(LocalDateTime.now().plusDays(3)).build());
        when(properties.countByUser_UserId(ownerId)).thenReturn(50L);

        assertThatCode(() -> service.requirePropertySlot(ownerId)).doesNotThrowAnyException();
        assertThat(service.allows(ownerId, Feature.IRRIGATION)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"0,true", "1,false", "2,false"})
    void normalAllowsOneProperty(long owned, boolean allowed) {
        assertSlot(normal, owned, allowed);
    }

    @ParameterizedTest
    @CsvSource({"4,true", "5,false"})
    void medioAllowsFiveProperties(long owned, boolean allowed) {
        assertSlot(medio, owned, allowed);
    }

    @Test
    void premiumHasNoPropertyLimit() {
        assertSlot(premium, 1_000, true);
    }

    @ParameterizedTest
    @CsvSource({
            "BASE,true,true,true", "REPORTS,false,true,true", "SPRAY_WINDOW,false,true,true", "NDVI,false,true,true",
            "ALERTS,false,true,true", "TEAM,false,true,true", "IRRIGATION,false,false,true", "EARLY_ACCESS,false,false,true"})
    void featureMatrixOfTheThreePlans(Feature feature, boolean inNormal, boolean inMedio, boolean inPremium) {
        contracted(normal);
        assertThat(service.allows(ownerId, feature)).isEqualTo(inNormal);
        contracted(medio);
        assertThat(service.allows(ownerId, feature)).isEqualTo(inMedio);
        contracted(premium);
        assertThat(service.allows(ownerId, feature)).isEqualTo(inPremium);
    }

    @Test
    void propertiesAboveTheLimitLoseFeaturesAndOldestStaysActive() {
        contracted(normal);

        assertThat(service.allows(ownerId, firstId, Feature.BASE)).isTrue();
        assertThat(service.allows(ownerId, secondId, Feature.BASE)).isFalse();
    }

    @Test
    void memberUsesOwnerPlanWhenItIncludesTeam() {
        contracted(medio);
        property(firstId, true);
        when(members.existsByProperty_PropertyIdAndUser_UserId(firstId, memberId)).thenReturn(true);

        assertThat(service.allows(memberId, firstId, Feature.NDVI)).isTrue();
        assertThat(service.allows(memberId, firstId, Feature.IRRIGATION)).isFalse();
    }

    @Test
    void memberIsBlockedWhenOwnerPlanHasNoTeam() {
        contracted(normal);
        property(firstId, true);
        when(members.existsByProperty_PropertyIdAndUser_UserId(firstId, memberId)).thenReturn(true);

        assertThat(service.allows(memberId, firstId, Feature.BASE)).isFalse();
        assertThat(service.ownerHasTeam(ownerId)).isFalse();
    }

    @Test
    void ownerWithoutSubscriptionRecordCountsAsTrialWithoutWriting() {
        when(subscriptions.findByUserId(ownerId)).thenReturn(Optional.empty());

        assertThat(service.ownerHasTeam(ownerId)).isTrue();
    }

    private void assertSlot(Plan plan, long owned, boolean allowed) {
        contracted(plan);
        when(properties.countByUser_UserId(ownerId)).thenReturn(owned);
        if (allowed) assertThatCode(() -> service.requirePropertySlot(ownerId)).doesNotThrowAnyException();
        else assertThatThrownBy(() -> service.requirePropertySlot(ownerId)).isInstanceOf(ResponseStatusException.class);
    }

    private void contracted(Plan plan) {
        subscription(Subscription.builder().status("PENDING").plan(plan).trialEndsAt(LocalDateTime.now().plusDays(5))
                .paidUntil(LocalDate.now().plusDays(5)).build());
    }

    private void subscription(Subscription subscription) {
        when(subscriptions.findByUserId(ownerId)).thenReturn(Optional.of(subscription));
    }

    private void property(UUID id, boolean team) {
        when(properties.findById(id)).thenReturn(Optional.of(Property.builder().propertyId(id).teamEnabled(team)
                .user(User.builder().userId(ownerId).build()).build()));
    }

    private Plan plan(String code, Integer maxProperties, Set<Feature> features) {
        return Plan.builder().code(code).name(code).monthlyPrice(BigDecimal.TEN).yearlyPrice(BigDecimal.TEN)
                .maxProperties(maxProperties).active(true).features(features).build();
    }
}
