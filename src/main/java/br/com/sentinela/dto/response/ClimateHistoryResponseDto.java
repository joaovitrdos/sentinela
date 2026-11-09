package br.com.sentinela.dto.response;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Clima do ponto da propriedade comparado com todos os anos disponíveis. "driestRank" 1 é o período mais seco da
 * série; a comparação usa o mesmo trecho do calendário em cada ano.
 */
public record ClimateHistoryResponseDto(
        LocalDate firstDate,
        LocalDate lastDate,
        int years,
        Period last30Days,
        List<MonthSummary> recentMonths,
        List<MonthNormal> normals,
        List<DataSourceDto> sources
) {
    public record Period(LocalDate from, LocalDate to, double rainMm, double normalRainMm, Double anomalyPercent,
                         int driestRank, int yearsCompared) {}

    public record MonthSummary(YearMonth month, boolean complete, double rainMm, double normalRainMm, Double anomalyPercent,
                               int driestRank, int yearsCompared, double meanMaxTemperatureC, double normalMaxTemperatureC) {}

    public record MonthNormal(int month, double meanRainMm, double minRainMm, int minYear, double maxRainMm, int maxYear) {}
}
