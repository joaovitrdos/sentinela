package br.com.sentinela.dto.response;

import java.time.LocalDate;
import java.util.List;

public record PropertyMonitoringDto(
        boolean configured,
        String status,
        String message,
        LocalDate from,
        LocalDate to,
        List<Double> bounds,
        Double resolutionMeters,
        List<Observation> observations,
        LocalDate latestDate,
        LocalDate previousDate,
        Double previousMeanNdvi,
        Double changeNdvi,
        String source) {

    public record Observation(
            LocalDate date,
            double meanNdvi,
            double minNdvi,
            double maxNdvi,
            Double validPixelPercentage) {
    }
}
