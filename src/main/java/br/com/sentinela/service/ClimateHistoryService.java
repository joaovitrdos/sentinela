package br.com.sentinela.service;

import br.com.sentinela.dto.response.ClimateHistoryResponseDto;
import br.com.sentinela.dto.response.ClimateHistoryResponseDto.MonthNormal;
import br.com.sentinela.dto.response.ClimateHistoryResponseDto.MonthSummary;
import br.com.sentinela.dto.response.ClimateHistoryResponseDto.Period;
import br.com.sentinela.dto.response.DataSourceDto;
import br.com.sentinela.entity.Property;
import br.com.sentinela.service.NasaPowerService.DailyClimate;
import br.com.sentinela.exception.ClimateHistoryUnavailableException;
import br.com.sentinela.util.LogUtil;
import br.com.sentinela.util.Util;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Histórico de clima desde 1981 no ponto da propriedade: responde "este período está pior que o normal?". */
@Service
public class ClimateHistoryService {
    static final LocalDate SERIES_START = LocalDate.of(1981, 1, 1);
    private static final int RECENT_MONTHS = 12;
    private static final int LAST_DAYS = 30;

    private final PropertyService propertyService;
    private final NasaPowerService nasaPower;
    private final Clock clock;

    public ClimateHistoryService(PropertyService propertyService, NasaPowerService nasaPower, @Qualifier("sentinelaClock") Clock clock) {
        this.propertyService = propertyService;
        this.nasaPower = nasaPower;
        this.clock = clock;
    }

    public ClimateHistoryResponseDto history(UUID propertyId, UUID userId) {
        Property property = propertyService.requireView(propertyId, userId);
        List<DailyClimate> days;
        try {
            days = nasaPower.daily(property.getLatitude(), property.getLongitude(), SERIES_START, LocalDate.now(clock));
        } catch (RuntimeException exception) {
            LogUtil.warn("NASA POWER indisponível para o histórico de clima", exception);
            throw new ClimateHistoryUnavailableException();
        }
        if (Util.isNullOrEmpty(days)) {
            LogUtil.warn("NASA POWER sem dados para a propriedade {0}", propertyId);
            throw new ClimateHistoryUnavailableException();
        }
        return summarize(days);
    }

    static ClimateHistoryResponseDto summarize(List<DailyClimate> days) {
        Series series = new Series(days);
        LocalDate last = series.last;

        LocalDate windowStart = last.minusDays(LAST_DAYS - 1);
        Comparison recent = series.compare(windowStart, last);
        Period last30 = Util.isNull(recent) ? null : new Period(windowStart, last, round(recent.rain), round(recent.normalRain),
                recent.anomaly(), recent.rank, recent.years);

        List<MonthSummary> months = new ArrayList<>();
        YearMonth current = YearMonth.from(last);
        for (int back = RECENT_MONTHS - 1; back >= 0; back--) {
            YearMonth month = current.minusMonths(back);
            LocalDate end = month.atEndOfMonth().isAfter(last) ? last : month.atEndOfMonth();
            Comparison comparison = series.compare(month.atDay(1), end);
            if (Util.isNull(comparison)) continue;
            months.add(new MonthSummary(month, end.equals(month.atEndOfMonth()), round(comparison.rain), round(comparison.normalRain),
                    comparison.anomaly(), comparison.rank, comparison.years, round(comparison.maxTemperature),
                    round(comparison.normalMaxTemperature)));
        }

        return new ClimateHistoryResponseDto(series.first, last, last.getYear() - series.first.getYear() + 1, last30, months,
                series.normals(), List.of(DataSourceDto.NASA_POWER));
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private record Window(double rain, double maxTemperature) {}

    private record Comparison(double rain, double normalRain, double maxTemperature, double normalMaxTemperature, int rank, int years) {
        Double anomaly() {
            return normalRain <= 0 ? null : round((rain - normalRain) / normalRain * 100);
        }
    }

    private static final class Series {
        private final Map<LocalDate, DailyClimate> byDate = new HashMap<>();
        private final LocalDate first;
        private final LocalDate last;

        Series(List<DailyClimate> days) {
            LocalDate min = null;
            LocalDate max = null;
            for (DailyClimate day : days) {
                byDate.put(day.date(), day);
                if (Util.isNull(min) || day.date().isBefore(min)) min = day.date();
                if (Util.isNull(max) || day.date().isAfter(max)) max = day.date();
            }
            first = min;
            last = max;
        }

        /** Só soma trechos completos: um dia faltando faria o período parecer mais seco do que foi. */
        Window window(LocalDate from, LocalDate to) {
            double rain = 0;
            double maxTemperature = 0;
            int count = 0;
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                DailyClimate day = byDate.get(date);
                if (Util.anyNull(day)) return null;
                rain += day.rainMm();
                maxTemperature += day.maxTemperatureC();
                count++;
            }
            return count == 0 ? null : new Window(rain, maxTemperature / count);
        }

        Comparison compare(LocalDate from, LocalDate to) {
            Window current = window(from, to);
            if (Util.isNull(current)) return null;
            List<Window> others = new ArrayList<>();
            for (int years = 1; !from.minusYears(years).isBefore(first); years++) {
                Window other = window(from.minusYears(years), to.minusYears(years));
                if (Util.nonNull(other)) others.add(other);
            }
            if (Util.isNullOrEmpty(others)) return null;
            double normalRain = others.stream().mapToDouble(Window::rain).average().orElse(0);
            double normalMax = others.stream().mapToDouble(Window::maxTemperature).average().orElse(0);
            int rank = 1 + (int) others.stream().filter(other -> other.rain() < current.rain()).count();
            return new Comparison(current.rain(), normalRain, current.maxTemperature(), normalMax, rank, others.size() + 1);
        }

        /** Média e extremos de chuva de cada mês do calendário, só com meses completos. */
        List<MonthNormal> normals() {
            List<MonthNormal> normals = new ArrayList<>();
            for (int month = 1; month <= 12; month++) {
                double total = 0;
                int count = 0;
                double min = Double.MAX_VALUE;
                double max = -1;
                int minYear = 0;
                int maxYear = 0;
                for (int year = first.getYear(); year <= last.getYear(); year++) {
                    YearMonth yearMonth = YearMonth.of(year, month);
                    Window window = window(yearMonth.atDay(1), yearMonth.atEndOfMonth());
                    if (Util.isNull(window)) continue;
                    total += window.rain();
                    count++;
                    if (window.rain() < min) { min = window.rain(); minYear = year; }
                    if (window.rain() > max) { max = window.rain(); maxYear = year; }
                }
                if (count > 0) normals.add(new MonthNormal(month, round(total / count), round(min), minYear, round(max), maxYear));
            }
            return normals;
        }
    }
}
