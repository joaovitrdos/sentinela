package br.com.sentinela.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.time.Clock;
import java.util.TimeZone;

@Configuration
public class TimeZoneConfig {

    @Bean("sentinelaClock")
    public Clock sentinelaClock(@Qualifier("sentinelaTimeZone") ZoneId zoneId) {
        return Clock.system(zoneId);
    }

    @Bean("sentinelaTimeZone")
    public ZoneId sentinelaTimeZone(@Value("${sentinela.time-zone:America/Sao_Paulo}") String timeZone) {
        ZoneId zoneId = ZoneId.of(timeZone);
        // LocalDateTime.now()/LocalDate.now() (entidades, servicos, relatorios) usam o fuso padrao da JVM.
        // Alinha-o ao fuso da aplicacao para nao depender do TZ do container (UTC).
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
        return zoneId;
    }
}
