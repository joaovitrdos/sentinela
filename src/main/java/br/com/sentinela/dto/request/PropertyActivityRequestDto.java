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
        @Pattern(regexp = "IRRIGATION|RAINFALL|INSPECTION|INCIDENT")
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
        UUID plotId
) {
}
