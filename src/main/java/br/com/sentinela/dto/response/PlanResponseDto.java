package br.com.sentinela.dto.response;

import br.com.sentinela.enums.Feature;
import java.math.BigDecimal;
import java.util.Set;

public record PlanResponseDto(String code, String name, BigDecimal monthlyPrice, BigDecimal yearlyPrice,
                              Integer maxProperties, Set<Feature> features) {}
