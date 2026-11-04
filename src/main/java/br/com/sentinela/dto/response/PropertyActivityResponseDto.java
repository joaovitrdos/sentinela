package br.com.sentinela.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.time.LocalTime;
import java.util.UUID;

public record PropertyActivityResponseDto(
        UUID activityId,
        String type,
        LocalDate date,
        LocalTime time,
        String notes,
        Double amountMm,
        LocalDateTime createdAt,
        UUID plotId
) {
}
