package br.com.sentinela.service;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.LocalTime;
import java.sql.Time;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Run explicitly with -Drun.report.db.test=true when the local PostgreSQL is available. */
class FieldHistoryReportDatabaseTest {
    @Test
    @EnabledIfSystemProperty(named = "run.report.db.test", matches = "true")
    void fillsAndExportsFieldHistoryAgainstPostgres() throws Exception {
        Properties env = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(".env"))) { env.load(reader); }
        String url = "jdbc:postgresql://" + env.getProperty("DB_HOST") + ":" + env.getProperty("DB_PORT")
                + "/" + env.getProperty("DB_NAME");
        try (var connection = DriverManager.getConnection(url, env.getProperty("DB_USERNAME"), env.getProperty("DB_PASSWORD"));
             var input = getClass().getResourceAsStream("/reports/field_history/sentinela_field_history_by_season.jrxml")) {
            UUID userId = UUID.randomUUID();
            UUID propertyId = null;
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT user_id, property_id FROM tb_property WHERE user_id IS NOT NULL LIMIT 1")) {
                if (result.next()) {
                    userId = result.getObject(1, UUID.class);
                    propertyId = result.getObject(2, UUID.class);
                }
            }
            LocalDate today = LocalDate.now();
            Map<String, Object> parameters = new java.util.HashMap<>();
            parameters.put("REPORT_TITLE", "Histórico de Campo por Safra");
            parameters.put("PERIOD_LABEL", "Teste local");
            parameters.put("TIME_LABEL", "Todos os horários");
            parameters.put("OWNER_ID", userId.toString());
            parameters.put("ALL_PROPERTIES", true);
            parameters.put("PROPERTY_IDS", List.of());
            parameters.put("FROM_DATE", Date.valueOf(today.minusMonths(6)));
            parameters.put("TO_DATE", Date.valueOf(today));
            parameters.put("FROM_TIME", null);
            parameters.put("TO_TIME", null);
            var print = JasperFillManager.fillReport(JasperCompileManager.compileReport(input), parameters, connection);
            assertThat(JasperExportManager.exportReportToPdf(print)).isNotEmpty();
            if (propertyId != null) {
                parameters.put("ALL_PROPERTIES", false);
                parameters.put("PROPERTY_IDS", List.of(propertyId));
                parameters.put("FROM_TIME", Time.valueOf(LocalTime.of(8, 0)));
                parameters.put("TO_TIME", Time.valueOf(LocalTime.of(17, 0)));
                try (var selectedInput = getClass().getResourceAsStream("/reports/field_history/sentinela_field_history_by_season.jrxml")) {
                    var selected = JasperFillManager.fillReport(JasperCompileManager.compileReport(selectedInput), parameters, connection);
                    assertThat(JasperExportManager.exportReportToPdf(selected)).isNotEmpty();
                }
            }
        }
    }
}
