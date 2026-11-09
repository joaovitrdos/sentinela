package br.com.sentinela.service;

import br.com.sentinela.dto.response.ClimateHistoryResponseDto;
import br.com.sentinela.dto.response.ClimateHistoryResponseDto.MonthSummary;
import br.com.sentinela.service.NasaPowerService.DailyClimate;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClimateHistoryServiceTest {

    /** Chove 2 mm por dia em todo ano, menos no ano indicado, que recebe "rainInDryYear". */
    private static List<DailyClimate> series(LocalDate from, LocalDate to, int dryYear, double rainInDryYear) {
        List<DailyClimate> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            days.add(new DailyClimate(date, 30, 18, date.getYear() == dryYear ? rainInDryYear : 2));
        }
        return days;
    }

    @Test
    void driestYearRanksFirstAgainstTheSameCalendarWindow() {
        ClimateHistoryResponseDto result = ClimateHistoryService.summarize(
                series(LocalDate.of(2016, 1, 1), LocalDate.of(2025, 10, 31), 2025, 1));

        assertThat(result.years()).isEqualTo(10);
        assertThat(result.last30Days().rainMm()).isEqualTo(30.0);
        assertThat(result.last30Days().normalRainMm()).isEqualTo(60.0);
        assertThat(result.last30Days().anomalyPercent()).isEqualTo(-50.0);
        assertThat(result.last30Days().driestRank()).isEqualTo(1);
        assertThat(result.last30Days().yearsCompared()).isEqualTo(10);

        MonthSummary october = result.recentMonths().getLast();
        assertThat(october.month()).isEqualTo(YearMonth.of(2025, 10));
        assertThat(october.complete()).isTrue();
        assertThat(october.rainMm()).isEqualTo(31.0);
        assertThat(october.normalRainMm()).isEqualTo(62.0);
    }

    @Test
    void partialMonthIsComparedOnlyWithTheSameDaysOfOtherYears() {
        ClimateHistoryResponseDto result = ClimateHistoryService.summarize(
                series(LocalDate.of(2020, 1, 1), LocalDate.of(2025, 11, 5), 0, 0));

        MonthSummary november = result.recentMonths().getLast();
        assertThat(november.complete()).isFalse();
        assertThat(november.rainMm()).isEqualTo(10.0);
        assertThat(november.normalRainMm()).isEqualTo(10.0);
        assertThat(november.anomalyPercent()).isEqualTo(0.0);
    }

    @Test
    void normalsIgnoreIncompleteMonthsAndKeepTheExtremeYears() {
        List<DailyClimate> days = series(LocalDate.of(2020, 1, 1), LocalDate.of(2024, 12, 31), 2022, 5);
        days.removeIf(day -> day.date().equals(LocalDate.of(2023, 3, 10)));

        ClimateHistoryResponseDto result = ClimateHistoryService.summarize(days);

        var march = result.normals().get(2);
        assertThat(march.month()).isEqualTo(3);
        assertThat(march.minRainMm()).isEqualTo(62.0);
        assertThat(march.maxRainMm()).isEqualTo(155.0);
        assertThat(march.maxYear()).isEqualTo(2022);
        assertThat(march.meanRainMm()).isEqualTo(85.3);
    }
}
