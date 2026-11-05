package br.com.sentinela.service;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportTemplateCompilationTest {
    @Test
    void weatherTemplateRendersCurrentForecastAndTimeZoneSamples() throws Exception {
        try (var input = getClass().getResourceAsStream("/reports/weather/weather_property_summary.jrxml")) {
            assertThat(input).isNotNull();
            var template = JasperCompileManager.compileReport(input);
            for (String[] values : new String[][] {
                    {"Condição", "Temperatura", "Sensação", "Umidade", "Precipitação", "Vento"},
                    {"Data", "Mínima", "Máxima", "Chuva Prevista", "Umidade", "Condição"},
                    {"Localidade", "Região", "País", "Fuso Horário", "Hora Local", "Coordenadas"}
            }) {
                var parameters = new java.util.HashMap<String, Object>();
                parameters.put("REPORT_TITLE", "WeatherAPI · Relatório de amostra");
                parameters.put("PROPERTY_LABEL", "Fazenda Exemplo · Goiânia/GO");
                parameters.put("GENERATED_AT", "04/11/2026 15:00");
                var row = new java.util.HashMap<String, Object>();
                for (int i = 0; i < values.length; i++) {
                    parameters.put("HEADER" + (i + 1), values[i]);
                    row.put("c" + (i + 1), values[i]);
                }
                var print = JasperFillManager.fillReport(template, parameters,
                        new JRMapCollectionDataSource(java.util.List.of(row)));
                assertThat(JasperExportManager.exportReportToPdf(print))
                        .startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            }
        }
    }

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

    @Test
    void fieldHistoryTemplateCompiles() throws Exception {
        try (var input = getClass().getResourceAsStream(
                "/reports/field_history/sentinela_field_history_by_season.jrxml")) {
            assertThat(input).isNotNull();
            assertThat(JasperCompileManager.compileReport(input)).isNotNull();
        }
    }

    @Test
    void fieldHistoryRendersReadableSample() throws Exception {
        var parameters = new java.util.HashMap<String, Object>();
        parameters.put("REPORT_TITLE", "AMOSTRA - Histórico de Campo por Safra");
        parameters.put("PERIOD_LABEL", "01/09/2026 - 04/11/2026");
        parameters.put("TIME_LABEL", "Todos os horários");
        var row = new java.util.HashMap<String, Object>();
        row.put("event_date", java.sql.Date.valueOf("2026-11-04"));
        row.put("property_name", "Fazenda Boa Esperança");
        row.put("plot_name", "Talhão Norte");
        row.put("season_name", "Milho / AG 1051");
        row.put("record_type", "Adubação");
        row.put("responsible_name", "Ana Técnica");
        row.put("status_name", "Registrada");
        row.put("detail", "Aplicação na área irrigada | Insumo: NPK 04-14-08 120 kg | Fotos: 2");
        try (var input = getClass().getResourceAsStream(
                "/reports/field_history/sentinela_field_history_by_season.jrxml")) {
            var template = JasperCompileManager.compileReport(input);
            var print = JasperFillManager.fillReport(template, parameters,
                    new JRMapCollectionDataSource(java.util.List.of(row)));
            byte[] pdf = JasperExportManager.exportReportToPdf(print);
            assertThat(pdf).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            if (Boolean.getBoolean("render.report.sample")) {
                var directory = java.nio.file.Path.of("tmp", "pdfs");
                java.nio.file.Files.createDirectories(directory);
                java.nio.file.Files.write(directory.resolve("historico-campo-amostra.pdf"), pdf);
            }
        }
    }
}
