package br.com.sentinela.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.time.Clock;

@Configuration
public class TimeZoneConfig {

    @Bean("sentinelaClock")
    public Clock sentinelaClock(@Qualifier("sentinelaTimeZone") ZoneId zoneId) {
        return Clock.system(zoneId);
    }

    @Bean("sentinelaTimeZone")
    public ZoneId sentinelaTimeZone(@Value("${sentinela.time-zone:America/Sao_Paulo}") String timeZone) {
        return ZoneId.of(timeZone);
    }
}
