package br.com.sentinela.service;

import br.com.sentinela.dto.response.BillingStatusResponseDto;
import br.com.sentinela.dto.response.PlanResponseDto;
import br.com.sentinela.entity.Plan;
import br.com.sentinela.entity.Subscription;
import br.com.sentinela.enums.Feature;
import br.com.sentinela.repository.PlanRepository;
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
    private final TeamService team;

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

    @Transactional
    public boolean allows(UUID userId, UUID propertyId, Feature feature) {
        return allows(billingUser(userId, propertyId), feature);
    }

    @Transactional
    public boolean allows(UUID userId, Feature feature) {
        Plan plan = effectivePlan(userId);
        return plan != null && plan.getFeatures().contains(feature);
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

    private Plan effectivePlan(UUID userId) {
        return resolve(subscriptionOf(userId), plans.findById(TRIAL_PLAN).orElseThrow(), LocalDateTime.now());
    }

    /** Depende só das datas, para que um webhook perdido não deixe o plano liberado para sempre. */
    static Plan resolve(Subscription subscription, Plan trialPlan, LocalDateTime now) {
        if (now.isBefore(subscription.getTrialEndsAt())) return trialPlan;
        LocalDate paidUntil = subscription.getPaidUntil();
        if (paidUntil == null) return null;
        int grace = "CANCELED".equals(subscription.getStatus()) ? 0 : GRACE_DAYS;
        return now.toLocalDate().isAfter(paidUntil.plusDays(grace)) ? null : subscription.getPlan();
    }

    private UUID billingUser(UUID userId, UUID propertyId) {
        if (propertyId == null) return userId;
        return properties.findById(propertyId).filter(property -> team.role(property, userId) != null)
                .map(property -> property.getUser().getUserId()).orElse(userId);
    }
}
