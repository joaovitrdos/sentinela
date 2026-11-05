package br.com.sentinela.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record PropertyActivityRequestDto(
        @NotBlank
        @Pattern(regexp = "IRRIGATION|RAINFALL|INSPECTION|INCIDENT|PLANTING|FERTILIZATION|SPRAYING|HARVEST|OTHER")
        String type,

        @NotNull
        @PastOrPresent
        LocalDate date,

        LocalTime time,

        @NotBlank
        @Size(max = 1000)
        String notes,

        @PositiveOrZero
        Double amountMm,
        UUID plotId,
        UUID seasonId,
        UUID responsibleUserId,
        @Size(max = 120) String inputName,
        @jakarta.validation.constraints.DecimalMin("0.001") java.math.BigDecimal inputQuantity,
        @Size(max = 24) String inputUnit,
        @jakarta.validation.constraints.DecimalMin("0.001") java.math.BigDecimal harvestQuantity,
        @Size(max = 24) String harvestUnit
) {
    public PropertyActivityRequestDto(String type, LocalDate date, LocalTime time, String notes, Double amountMm, UUID plotId) {
        this(type, date, time, notes, amountMm, plotId, null, null, null, null, null, null, null);
    }
}
