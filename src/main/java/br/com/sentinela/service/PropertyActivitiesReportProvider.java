package br.com.sentinela.service;

import br.com.sentinela.util.Util;
import br.com.sentinela.report.PreparedReport;
import br.com.sentinela.report.ReportProvider;
import br.com.sentinela.repository.PropertyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class PropertyActivitiesReportProvider implements ReportProvider {

    public static final String CODE = "sentinela_properties_activities";
    private static final Set<String> ALLOWED_PARAMETERS = Set.of(
            "propertyIds", "allProperties", "fromDate", "toDate", "fromTime", "toTime");
    private final PropertyRepository propertyRepository;

    public PropertyActivitiesReportProvider(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String title() {
        return "Propriedades E Atividades";
    }

    @Override
    public String templatePath() {
        return "reports/properties_activities/sentinela_properties_activities.jrxml";
    }

    @Override
    public PreparedReport prepare(UUID userId, Map<String, Object> requested) {
        if (!ALLOWED_PARAMETERS.containsAll(requested.keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O relatório recebeu parâmetros não permitidos");
        }

        boolean allProperties = booleanValue(requested.get("allProperties"), "allProperties");
        List<UUID> propertyIds = propertyIds(requested.get("propertyIds"));
        if (!allProperties && Util.isNullOrEmpty(propertyIds)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione ao menos uma propriedade ou escolha todas");
        }
        if (allProperties && !Util.isNullOrEmpty(propertyIds)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie propriedades selecionadas ou escolha todas, não ambos");
        }
        validateAccessibleProperties(userId, propertyIds);
        LocalDate fromDate = date(requested.get("fromDate"), "fromDate");
        LocalDate toDate = date(requested.get("toDate"), "toDate");
        LocalTime fromTime = time(requested.get("fromTime"), "fromTime");
        LocalTime toTime = time(requested.get("toTime"), "toTime");
        validate(fromDate, toDate, fromTime, toTime);

        Map<String, Object> parameters = new LinkedHashMap<>();
        // O owner vem da sessão autenticada, nunca do JSON enviado pelo navegador.
        parameters.put("OWNER_ID", userId.toString());
        parameters.put("ALL_PROPERTIES", allProperties);
        parameters.put("PROPERTY_IDS", propertyIds);
        parameters.put("FROM_DATE", Date.valueOf(fromDate));
        parameters.put("TO_DATE", Date.valueOf(toDate));
        parameters.put("FROM_TIME", Util.isNull(fromTime) ? null : Time.valueOf(fromTime));
        parameters.put("TO_TIME", Util.isNull(toTime) ? null : Time.valueOf(toTime));
        parameters.put("REPORT_TITLE", title());
        parameters.put("PERIOD_LABEL", fromDate + " - " + toDate);
        parameters.put("TIME_LABEL", Util.isNull(fromTime) ? "Todos os horários" : fromTime + " - " + toTime);
        return PreparedReport.jdbc(parameters);
    }

    private void validate(LocalDate fromDate, LocalDate toDate, LocalTime fromTime, LocalTime toTime) {
        if (Util.isNull(fromDate) || Util.isNull(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a data inicial e a data final");
        }
        if (fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A data inicial deve ser anterior ou igual à data final");
        }
        if ((Util.isNull(fromTime)) != (Util.isNull(toTime))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o horário inicial e final");
        }
        if (Util.nonNull(fromTime) && fromTime.isAfter(toTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O horário inicial deve ser anterior ou igual ao final");
        }
    }

    private List<UUID> propertyIds(Object value) {
        if (Util.isNull(value)) return List.of();
        if (!(value instanceof Collection<?> values)) throw invalid("propertyIds");
        List<UUID> propertyIds = new ArrayList<>();
        for (Object item : values) {
            if (Util.isNullOrBlank(item)) throw invalid("propertyIds");
            try {
                UUID id = UUID.fromString(item.toString());
                if (propertyIds.contains(id)) throw invalid("propertyIds");
                propertyIds.add(id);
            } catch (IllegalArgumentException exception) {
                throw invalid("propertyIds");
            }
        }
        return List.copyOf(propertyIds);
    }

    private void validateAccessibleProperties(UUID userId, List<UUID> propertyIds) {
        if (Util.isNullOrEmpty(propertyIds)) return;
        Set<UUID> accessibleIds = new HashSet<>();
        propertyRepository.findAllAccessibleByIds(propertyIds, userId)
                .forEach(property -> accessibleIds.add(property.getPropertyId()));
        if (!accessibleIds.equals(new HashSet<>(propertyIds))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Você não tem acesso a uma ou mais propriedades selecionadas");
        }
    }

    private boolean booleanValue(Object value, String name) {
        if (value instanceof Boolean bool) return bool;
        if (Util.isNull(value)) return false;
        if ("true".equalsIgnoreCase(value.toString()) || "false".equalsIgnoreCase(value.toString())) {
            return Boolean.parseBoolean(value.toString());
        }
        throw invalid(name);
    }

    private LocalDate date(Object value, String name) {
        if (Util.isNullOrBlank(value)) return null;
        try {
            return LocalDate.parse(value.toString());
        } catch (RuntimeException exception) {
            throw invalid(name);
        }
    }

    private LocalTime time(Object value, String name) {
        if (Util.isNullOrBlank(value)) return null;
        try {
            return LocalTime.parse(value.toString());
        } catch (RuntimeException exception) {
            throw invalid(name);
        }
    }

    private ResponseStatusException invalid(String name) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parâmetro inválido: " + name);
    }
}
