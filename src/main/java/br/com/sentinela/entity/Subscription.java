package br.com.sentinela.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "tb_subscription")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Subscription {
    @Id @Column(name = "subscription_id") private UUID subscriptionId;
    @Column(name = "user_id", nullable = false, unique = true) private UUID userId;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "plan_code") private Plan plan;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "pending_plan_code") private Plan pendingPlan;
    @Column(name = "billing_cycle", length = 8) private String cycle;
    @Column(name = "pending_billing_cycle", length = 8) private String pendingCycle;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "trial_ends_at", nullable = false) private LocalDateTime trialEndsAt;
    @Column(name = "paid_until") private LocalDate paidUntil;
    @Column(name = "asaas_customer_id", length = 64) private String asaasCustomerId;
    @Column(name = "asaas_subscription_id", length = 64) private String asaasSubscriptionId;
    @Column(name = "asaas_checkout_id", length = 64) private String checkoutId;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "checkout_plan_code") private Plan checkoutPlan;
    @Column(name = "checkout_billing_cycle", length = 8) private String checkoutCycle;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
