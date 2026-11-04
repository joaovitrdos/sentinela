package br.com.sentinela.service;

import net.sf.jasperreports.engine.JasperCompileManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportTemplateCompilationTest {
    @Test
    void propertyTemplatesCompileWithPlotAndRainfallFields() throws Exception {
        for (String template : new String[] {
                "sentinela_properties_activities.jrxml",
                "sentinela_activity_summary_by_property.jrxml",
                "sentinela_irrigation_monthly_summary.jrxml"}) {
            try (var input = getClass().getResourceAsStream("/reports/properties_activities/" + template)) {
                assertThat(input).isNotNull();
                assertThat(JasperCompileManager.compileReport(input)).isNotNull();
            }
        }
    }
}
