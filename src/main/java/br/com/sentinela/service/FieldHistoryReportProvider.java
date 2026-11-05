package br.com.sentinela.service;

import br.com.sentinela.report.PreparedReport;
import br.com.sentinela.report.ReportProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;

@Component @RequiredArgsConstructor
public class FieldHistoryReportProvider implements ReportProvider {
    public static final String CODE = "sentinela_field_history_by_season";
    private final PropertyReportFilterSupport filters;

    @Override public String code() { return CODE; }
    @Override public String title() { return "Histórico de Campo por Safra"; }
    @Override public String templatePath() { return "reports/field_history/sentinela_field_history_by_season.jrxml"; }

    @Override
    public PreparedReport prepare(UUID userId, Map<String, Object> requestedParameters) {
        var filter = filters.validate(userId, requestedParameters);
        var parameters = filters.jasperParameters(filter, title());
        if (filter.fromTime() != null)
            parameters.put("TIME_LABEL", parameters.get("TIME_LABEL") + " (registros sem horário incluídos)");
        return PreparedReport.jdbc(parameters);
    }
}
