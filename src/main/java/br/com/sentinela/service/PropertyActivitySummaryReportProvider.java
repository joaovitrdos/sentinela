package br.com.sentinela.service;

import br.com.sentinela.report.PreparedReport;
import br.com.sentinela.report.ReportProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class PropertyActivitySummaryReportProvider implements ReportProvider {

    public static final String CODE = "sentinela_activity_summary_by_property";

    private final PropertyReportFilterSupport filterSupport;

    public PropertyActivitySummaryReportProvider(PropertyReportFilterSupport filterSupport) {
        this.filterSupport = filterSupport;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String title() {
        return "Resumo De Atividades Por Propriedade";
    }

    @Override
    public String templatePath() {
        return "reports/properties_activities/sentinela_activity_summary_by_property.jrxml";
    }

    @Override
    public PreparedReport prepare(UUID userId, Map<String, Object> requestedParameters) {
        var filters = filterSupport.validate(userId, requestedParameters);
        return PreparedReport.jdbc(filterSupport.jasperParameters(filters, title()));
    }
}
