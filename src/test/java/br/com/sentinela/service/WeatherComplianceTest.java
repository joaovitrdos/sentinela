package br.com.sentinela.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.sentinela.dto.response.CurrentWeatherDto;
import br.com.sentinela.dto.response.ForecastWeatherDto;
import br.com.sentinela.dto.response.WeatherTimeZoneDto;
import br.com.sentinela.util.WeatherAttribution;
import java.util.HashMap;
import java.util.List;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class WeatherComplianceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final WeatherApiUsageService usage = new WeatherApiUsageService(jdbc, 100_000);

    @Test
    void disclaimerCoversEveryPointRequiredByTheTerms() {
        assertThat(WeatherAttribution.DISCLAIMER).contains("fins informativos gerais", "probabilísticas",
                "podem não ser precisas", "única base", "segurança pessoal", "aviação", "navegação marítima",
                "planejamento de emergências", "serviços meteorológicos oficiais");
    }

    @Test
    void everyWeatherResponseCreditsTheSource() {
        var current = new CurrentWeatherDto("c", "r", "p", 0.0, 0.0, 0.0, 0.0, 0, 0.0, 0.0, 0.0, 0.0, "x", "i", "u");
        var forecast = new ForecastWeatherDto(null, null, null);
        var timeZone = new WeatherTimeZoneDto("c", "r", "p", 0.0, 0.0, "t", 0L, "l");

        assertThat(List.of(current.source(), forecast.source(), timeZone.source())).containsOnly("WeatherAPI.com");
        assertThat(List.of(current.disclaimer(), forecast.disclaimer())).containsOnly(WeatherAttribution.DISCLAIMER);
    }

    @Test
    void weatherPdfRendersWithTheDisclaimerInTheFooter() throws Exception {
        try (var input = getClass().getResourceAsStream("/reports/weather/weather_property_summary.jrxml")) {
            var template = JasperCompileManager.compileReport(input);
            var parameters = new HashMap<String, Object>();
            parameters.put("REPORT_TITLE", "WeatherAPI · Teste");
            var row = new HashMap<String, Object>();
            row.put("c1", "valor");
            var print = JasperFillManager.fillReport(template, parameters, new JRMapCollectionDataSource(List.of(row)));

            var texts = new StringBuilder();
            print.getPages().getFirst().getElements().forEach(element -> {
                if (element instanceof net.sf.jasperreports.engine.JRPrintText text) texts.append(text.getFullText()).append('\n');
            });
            assertThat(texts.toString()).contains(WeatherAttribution.DISCLAIMER);
            assertThat(JasperExportManager.exportReportToPdf(print)).isNotEmpty();
        }
    }

    @Test
    void automationStopsAtNinetyPercentOfTheQuota() {
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of(89_999L));
        assertThat(usage.automationAllowed()).isTrue();

        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of(90_000L));
        assertThat(usage.automationAllowed()).isFalse();
    }

    @Test
    void automationContinuesWithoutUsageData() {
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of());
        assertThat(usage.automationAllowed()).isTrue();

        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("banco fora"));
        assertThat(usage.automationAllowed()).isTrue();
    }
}
