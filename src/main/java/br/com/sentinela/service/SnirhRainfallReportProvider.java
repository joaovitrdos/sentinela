package br.com.sentinela.service;

import br.com.sentinela.util.Util;
import br.com.sentinela.dto.response.RainReadingResponseDto;
import br.com.sentinela.report.PreparedReport;
import br.com.sentinela.report.ReportProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class SnirhRainfallReportProvider implements ReportProvider {

    public static final String CODE = "snirh_rainfall_24h_by_station";
    private static final Set<String> ALLOWED_PARAMETERS = Set.of("state", "onlyAttention");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final RainService rainService;

    public SnirhRainfallReportProvider(RainService rainService) {
        this.rainService = rainService;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String title() {
        return "SNIRH · Chuva Acumulada Nas Últimas 24 Horas";
    }

    @Override
    public String templatePath() {
        return "reports/snirh/rainfall/snirh_rainfall_24h_by_station.jrxml";
    }

    @Override
    public PreparedReport prepare(UUID userId, Map<String, Object> requested) {
        if (!ALLOWED_PARAMETERS.containsAll(requested.keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O relatório recebeu parâmetros não permitidos");
        }
        String state = text(requested.get("state"));
        if (Util.nonNull(state) && !state.matches("[A-Za-z]{2}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe uma sigla de estado válida");
        }
        boolean onlyAttention = booleanValue(requested.get("onlyAttention"));
        List<Map<String, ?>> rows = new ArrayList<>();
        List<RainReadingResponseDto> readings = new ArrayList<>(rainService.getRain24h());
        readings.sort(Comparator.comparingDouble(RainReadingResponseDto::rainfallMm24h).reversed());
        for (RainReadingResponseDto reading : readings) {
            if (Util.nonNull(state) && !state.equalsIgnoreCase(reading.state())) continue;
            if (onlyAttention && !reading.requiresAttention()) continue;
            rows.add(toRow(reading));
        }

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("REPORT_TITLE", title());
        parameters.put("FILTER_LABEL", (Util.isNull(state) ? "Todos os estados" : "UF: " + state.toUpperCase(Locale.ROOT))
                + (onlyAttention ? " · Somente estações que exigem atenção" : ""));
        parameters.put("GENERATED_AT", DATE_TIME.format(OffsetDateTime.now()));
        parameters.put("TOTAL_STATIONS", rows.size());
        return new PreparedReport(parameters, rows);
    }

    private Map<String, Object> toRow(RainReadingResponseDto reading) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stationCode", Long.toString(reading.stationCode()));
        row.put("stationName", value(reading.stationName()));
        row.put("city", value(reading.city()));
        row.put("state", value(reading.state()));
        row.put("watershed", value(reading.watershed()));
        row.put("rainfallMm24h", String.format(Locale.forLanguageTag("pt-BR"), "%.1f mm", reading.rainfallMm24h()));
        row.put("readingClass", value(reading.readingClass()));
        row.put("lastReadingAt", Util.isNull(reading.lastReadingAt()) ? "Sem data" : DATE_TIME.format(reading.lastReadingAt()));
        row.put("ageHours", String.format(Locale.forLanguageTag("pt-BR"), "%.1f h", reading.ageHours()));
        return row;
    }

    private String text(Object value) {
        return Util.trimToNull(value);
    }

    private boolean booleanValue(Object value) {
        if (Util.isNull(value)) return false;
        if (value instanceof Boolean bool) return bool;
        if ("true".equalsIgnoreCase(value.toString()) || "false".equalsIgnoreCase(value.toString())) {
            return Boolean.parseBoolean(value.toString());
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parâmetro inválido: onlyAttention");
    }

    private String value(String value) {
        return Util.isNull(value) ? "" : value;
    }
}
