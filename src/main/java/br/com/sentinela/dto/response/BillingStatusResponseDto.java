package br.com.sentinela.dto.response;

import br.com.sentinela.enums.Feature;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/** {@code effectivePlan} nulo indica acesso somente leitura aos dados já cadastrados. */
public record BillingStatusResponseDto(String status, String effectivePlan, String contractedPlan, String pendingPlan,
                                       String cycle, LocalDateTime trialEndsAt, LocalDate paidUntil,
                                       Integer maxProperties, Set<Feature> features, boolean hasCustomer) {}
