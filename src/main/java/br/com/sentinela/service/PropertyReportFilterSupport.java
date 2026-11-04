package br.com.sentinela.service;

import br.com.sentinela.entity.Property;
import br.com.sentinela.repository.PropertyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class PropertyReportFilterSupport {

    private static final Set<String> ALLOWED_PARAMETERS = Set.of(
            "propertyIds", "allProperties", "fromDate", "toDate", "fromTime", "toTime");

    private final PropertyRepository propertyRepository;

    public PropertyReportFilterSupport(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    public Filter validate(UUID userId, Map<String, Object> requested) {
        if (!ALLOWED_PARAMETERS.containsAll(requested.keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O relatório recebeu parâmetros não permitidos");
        }

        boolean allProperties = booleanValue(requested.get("allProperties"), "allProperties");
        List<UUID> propertyIds = propertyIds(requested.get("propertyIds"));
        if (!allProperties && propertyIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione ao menos uma propriedade ou escolha todas");
        }
        if (allProperties && !propertyIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie propriedades selecionadas ou escolha todas, não ambos");
        }
        validateAccessibleProperties(userId, propertyIds);

        LocalDate fromDate = date(requested.get("fromDate"), "fromDate");
        LocalDate toDate = date(requested.get("toDate"), "toDate");
        LocalTime fromTime = time(requested.get("fromTime"), "fromTime");
        LocalTime toTime = time(requested.get("toTime"), "toTime");
        validateDatesAndTimes(fromDate, toDate, fromTime, toTime);

        return new Filter(userId, allProperties, propertyIds, fromDate, toDate, fromTime, toTime);
    }

    public Map<String, Object> jasperParameters(Filter filter, String title) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("OWNER_ID", filter.ownerId().toString());
        parameters.put("ALL_PROPERTIES", filter.allProperties());
        parameters.put("PROPERTY_IDS", filter.propertyIds());
        parameters.put("FROM_DATE", Date.valueOf(filter.fromDate()));
        parameters.put("TO_DATE", Date.valueOf(filter.toDate()));
        parameters.put("FROM_TIME", filter.fromTime() == null ? null : Time.valueOf(filter.fromTime()));
        parameters.put("TO_TIME", filter.toTime() == null ? null : Time.valueOf(filter.toTime()));
        parameters.put("REPORT_TITLE", title);
        parameters.put("PERIOD_LABEL", filter.fromDate() + " - " + filter.toDate());
        parameters.put("TIME_LABEL", filter.fromTime() == null
                ? "Todos os horários" : filter.fromTime() + " - " + filter.toTime());
        return parameters;
    }

    private void validateDatesAndTimes(LocalDate fromDate, LocalDate toDate, LocalTime fromTime, LocalTime toTime) {
        if (fromDate == null || toDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a data inicial e a data final");
        }
        if (fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A data inicial deve ser anterior ou igual à data final");
        }
        if ((fromTime == null) != (toTime == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o horário inicial e final");
        }
        if (fromTime != null && fromTime.isAfter(toTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O horário inicial deve ser anterior ou igual ao final");
        }
    }

    private List<UUID> propertyIds(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof Collection<?> values)) throw invalid("propertyIds");
        List<UUID> propertyIds = new ArrayList<>();
        for (Object item : values) {
            if (item == null || item.toString().isBlank()) throw invalid("propertyIds");
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
        if (propertyIds.isEmpty()) return;
        Set<UUID> accessibleIds = new HashSet<>();
        propertyRepository.findAllAccessibleByIds(propertyIds, userId)
                .forEach((Property property) -> accessibleIds.add(property.getPropertyId()));
        if (!accessibleIds.equals(new HashSet<>(propertyIds))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Você não tem acesso a uma ou mais propriedades selecionadas");
        }
    }

    private boolean booleanValue(Object value, String name) {
        if (value instanceof Boolean bool) return bool;
        if (value == null) return false;
        if ("true".equalsIgnoreCase(value.toString()) || "false".equalsIgnoreCase(value.toString())) {
            return Boolean.parseBoolean(value.toString());
        }
        throw invalid(name);
    }

    private LocalDate date(Object value, String name) {
        if (value == null || value.toString().isBlank()) return null;
        try {
            return LocalDate.parse(value.toString());
        } catch (RuntimeException exception) {
            throw invalid(name);
        }
    }

    private LocalTime time(Object value, String name) {
        if (value == null || value.toString().isBlank()) return null;
        try {
            return LocalTime.parse(value.toString());
        } catch (RuntimeException exception) {
            throw invalid(name);
        }
    }

    private ResponseStatusException invalid(String name) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parâmetro inválido: " + name);
    }

    public record Filter(UUID ownerId, boolean allProperties, List<UUID> propertyIds,
                         LocalDate fromDate, LocalDate toDate, LocalTime fromTime, LocalTime toTime) {
    }
}
