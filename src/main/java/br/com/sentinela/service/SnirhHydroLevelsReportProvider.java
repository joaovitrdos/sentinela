package br.com.sentinela.service;

import br.com.sentinela.dto.response.RiverLevelResponseDto;
import br.com.sentinela.report.PreparedReport;
import br.com.sentinela.report.ReportProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class SnirhHydroLevelsReportProvider implements ReportProvider {

    public static final String CODE = "snirh_hydro_levels_station_status";
    private static final Set<String> ALLOWED_PARAMETERS = Set.of("state", "onlyAttention");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ReferenceLevelService referenceLevelService;

    public SnirhHydroLevelsReportProvider(ReferenceLevelService referenceLevelService) {
        this.referenceLevelService = referenceLevelService;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String title() {
        return "SNIRH · Níveis Hidrológicos Por Estação";
    }

    @Override
    public String templatePath() {
        return "reports/snirh/hydro_levels/snirh_hydro_levels_station_status.jrxml";
    }

    @Override
    public PreparedReport prepare(UUID userId, Map<String, Object> requested) {
        if (!ALLOWED_PARAMETERS.containsAll(requested.keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O relatório recebeu parâmetros não permitidos");
        }
        String state = text(requested.get("state"));
        if (state != null && !state.matches("[A-Za-z]{2}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe uma sigla de estado válida");
        }
        boolean onlyAttention = booleanValue(requested.get("onlyAttention"));
        List<Map<String, ?>> rows = new ArrayList<>();
        for (RiverLevelResponseDto level : referenceLevelService.getRiverLevels()) {
            if (state != null && !state.equalsIgnoreCase(level.state())) continue;
            if (onlyAttention && !level.requiresAttention()) continue;
            rows.add(toRow(level));
        }

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("REPORT_TITLE", title());
        parameters.put("FILTER_LABEL", (state == null ? "Todos os estados" : "UF: " + state.toUpperCase(Locale.ROOT))
                + (onlyAttention ? " · Somente estações que exigem atenção" : ""));
        parameters.put("GENERATED_AT", DATE_TIME.format(OffsetDateTime.now()));
        parameters.put("TOTAL_STATIONS", rows.size());
        return new PreparedReport(parameters, rows);
    }

    private Map<String, Object> toRow(RiverLevelResponseDto level) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stationCode", Long.toString(level.stationCode()));
        row.put("stationName", value(level.stationName()));
        row.put("city", value(level.city()));
        row.put("state", value(level.state()));
        row.put("watershed", value(level.watershed()));
        row.put("levelCm", level.levelCm() == null ? "Sem leitura" : String.format(Locale.forLanguageTag("pt-BR"), "%.1f cm", level.levelCm()));
        row.put("status", value(level.status()));
        row.put("statusDescription", value(level.statusDescription()));
        row.put("lastReadingAt", level.lastReadingAt() == null ? "Sem data" : DATE_TIME.format(level.lastReadingAt()));
        row.put("ageHours", level.ageHours() == null ? "" : String.format(Locale.forLanguageTag("pt-BR"), "%.1f h", level.ageHours()));
        return row;
    }

    private String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private boolean booleanValue(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean bool) return bool;
        if ("true".equalsIgnoreCase(value.toString()) || "false".equalsIgnoreCase(value.toString())) {
            return Boolean.parseBoolean(value.toString());
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parâmetro inválido: onlyAttention");
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
