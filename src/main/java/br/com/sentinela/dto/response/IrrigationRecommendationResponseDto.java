package br.com.sentinela.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record IrrigationRecommendationResponseDto(
        UUID plotId,
        String plotName,
        String cropName,
        boolean cropKnown,
        double cropCoefficient,
        double recommendedMm,
        int daysWithoutData,
        List<Day> days,
        List<String> warnings,
        String method,
        String source,
        String disclaimer) {

    public record Day(LocalDate date, boolean forecast, double et0Mm, double cropDemandMm, double rainMm,
                      double irrigationMm, double deficitMm) {}
}
