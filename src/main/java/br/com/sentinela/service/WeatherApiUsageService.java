package br.com.sentinela.service;

import br.com.sentinela.util.LogUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/** Conta as chamadas à WeatherAPI, que o painel do fornecedor mostra só depois de consumidas. */
@Service
public class WeatherApiUsageService {
    private static final double WARNING_SHARE = 0.8;
    private static final double AUTOMATION_SHARE = 0.9;
    private static final int WARNING_STEP = 1_000;
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String INCREMENT = """
            INSERT INTO tb_weather_api_usage (usage_month, calls) VALUES (?, 1)
            ON CONFLICT (usage_month) DO UPDATE SET calls = tb_weather_api_usage.calls + 1
            RETURNING calls""";

    private final JdbcTemplate jdbc;
    private final long monthlyLimit;

    public WeatherApiUsageService(JdbcTemplate jdbc, @Value("${weather.api.monthly-limit:100000}") long monthlyLimit) {
        this.jdbc = jdbc;
        this.monthlyLimit = monthlyLimit;
    }

    /** Transação própria: a chamada pode partir de um fluxo somente leitura, como a geração de relatórios. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record() {
        Long calls = jdbc.queryForObject(INCREMENT, Long.class, month());
        if (calls != null && calls >= monthlyLimit * WARNING_SHARE && calls % WARNING_STEP == 0) {
            LogUtil.warn("WeatherAPI: " + calls + " de " + monthlyLimit + " chamadas usadas no mês");
        }
    }

    /** Reserva o fim da cota para as telas: os alertas automáticos param antes de o limite acabar. */
    public boolean automationAllowed() {
        try {
            List<Long> calls = jdbc.queryForList("SELECT calls FROM tb_weather_api_usage WHERE usage_month = ?", Long.class, month());
            return calls.isEmpty() || calls.getFirst() < monthlyLimit * AUTOMATION_SHARE;
        } catch (DataAccessException exception) {
            LogUtil.warn("Não foi possível ler o consumo da WeatherAPI");
            return true;
        }
    }

    private String month() {
        return YearMonth.now(ZONE).toString();
    }
}
