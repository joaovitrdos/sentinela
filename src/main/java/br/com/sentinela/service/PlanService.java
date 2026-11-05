package br.com.sentinela.service;

import br.com.sentinela.dto.response.BillingStatusResponseDto;
import br.com.sentinela.dto.response.PlanResponseDto;
import br.com.sentinela.entity.Plan;
import br.com.sentinela.entity.Property;
import br.com.sentinela.entity.Subscription;
import br.com.sentinela.enums.Feature;
import br.com.sentinela.repository.PlanRepository;
import br.com.sentinela.repository.PropertyMemberRepository;
import br.com.sentinela.repository.PropertyRepository;
import br.com.sentinela.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class PlanService {
    static final int TRIAL_DAYS = 7;
    static final int GRACE_DAYS = 3;
    private static final String TRIAL_PLAN = "PREMIUM";

    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final PropertyRepository properties;
    private final PropertyMemberRepository members;

    @Transactional(readOnly = true)
    public List<PlanResponseDto> list() {
        return plans.findAllByActiveTrueOrderByRank().stream()
                .map(plan -> new PlanResponseDto(plan.getCode(), plan.getName(), plan.getMonthlyPrice(), plan.getYearlyPrice(),
                        plan.getMaxProperties(), plan.getFeatures()))
                .toList();
    }

    @Transactional
    public Subscription subscriptionOf(UUID userId) {
        return subscriptions.findByUserId(userId).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            subscriptions.insertTrial(UUID.randomUUID(), userId, now.plusDays(TRIAL_DAYS), now);
            return subscriptions.findByUserId(userId).orElseThrow();
        });
    }

    /**
     * Regra de uma rota de propriedade: vale o plano do dono. Membros só entram se o plano dele incluir equipe, e
     * propriedades acima do limite ficam só para consulta, sem recursos do plano.
     */
    @Transactional
    public boolean allows(UUID userId, UUID propertyId, Feature feature) {
        Property property = propertyId == null ? null : properties.findById(propertyId).orElse(null);
        if (property == null) return allows(userId, feature);
        UUID ownerId = property.getUser().getUserId();
        boolean owner = ownerId.equals(userId);
        boolean member = !owner && Boolean.TRUE.equals(property.getTeamEnabled())
                && members.existsByProperty_PropertyIdAndUser_UserId(propertyId, userId);
        if (!owner && !member) return allows(userId, feature);

        Plan plan = effectivePlan(ownerId);
        if (plan == null || !plan.getFeatures().contains(feature)) return false;
        if (member && !plan.getFeatures().contains(Feature.TEAM)) return false;
        return withinLimit(plan, ownerId, propertyId);
    }

    @Transactional
    public boolean allows(UUID userId, Feature feature) {
        Plan plan = effectivePlan(userId);
        return plan != null && plan.getFeatures().contains(feature);
    }

    /** Somente leitura: é chamado de dentro de transações read-only e por isso não cria o registro de teste. */
    @Transactional(readOnly = true)
    public boolean ownerHasTeam(UUID ownerId) {
        Plan plan = subscriptions.findByUserId(ownerId).map(this::resolve).orElseGet(() -> plans.findById(TRIAL_PLAN).orElse(null));
        return plan != null && plan.getFeatures().contains(Feature.TEAM);
    }

    @Transactional
    public void requirePropertySlot(UUID ownerId) {
        Plan plan = effectivePlan(ownerId);
        if (plan == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seu plano não inclui este recurso");
        if (plan.getMaxProperties() != null && properties.countByUser_UserId(ownerId) >= plan.getMaxProperties()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seu plano permite até " + plan.getMaxProperties() + " propriedade(s)");
        }
    }

    @Transactional
    public BillingStatusResponseDto status(UUID userId) {
        Subscription subscription = subscriptionOf(userId);
        Plan effective = effectivePlan(userId);
        return new BillingStatusResponseDto(subscription.getStatus(), effective == null ? null : effective.getCode(),
                subscription.getPlan() == null ? null : subscription.getPlan().getCode(),
                subscription.getPendingPlan() == null ? null : subscription.getPendingPlan().getCode(),
                subscription.getCycle(), subscription.getTrialEndsAt(), subscription.getPaidUntil(),
                effective == null ? null : effective.getMaxProperties(), effective == null ? Set.of() : effective.getFeatures(),
                subscription.getAsaasCustomerId() != null);
    }

    private boolean withinLimit(Plan plan, UUID ownerId, UUID propertyId) {
        if (plan.getMaxProperties() == null) return true;
        int position = properties.findOwnedIdsOldestFirst(ownerId).indexOf(propertyId);
        return position >= 0 && position < plan.getMaxProperties();
    }

    private Plan effectivePlan(UUID userId) {
        return resolve(subscriptionOf(userId));
    }

    private Plan resolve(Subscription subscription) {
        return resolve(subscription, plans.findById(TRIAL_PLAN).orElseThrow(), LocalDateTime.now());
    }

    /**
     * Quem escolhe um plano sai do teste e passa a seguir as regras dele na hora. O resultado depende só das datas,
     * para que um webhook perdido não deixe o plano liberado para sempre.
     */
    static Plan resolve(Subscription subscription, Plan trialPlan, LocalDateTime now) {
        if (subscription.getPlan() == null) return now.isBefore(subscription.getTrialEndsAt()) ? trialPlan : null;
        LocalDate paidUntil = subscription.getPaidUntil();
        if (paidUntil == null) return null;
        int grace = "CANCELED".equals(subscription.getStatus()) ? 0 : GRACE_DAYS;
        return now.toLocalDate().isAfter(paidUntil.plusDays(grace)) ? null : subscription.getPlan();
    }
}
