package br.com.sentinela.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Settings for SNIRH rainfall readings accumulated over the last 24 hours. */
@ConfigurationProperties(prefix = "snirh.rain")
public record SnirhProperties(
        String baseUrl,
        @DefaultValue("1000") int pageSize,
        @DefaultValue("4") int maxRetries,
        @DefaultValue("2000") long retryDelayMs,
        @DefaultValue("300") double maxRainfallMm24h,
        @DefaultValue("10m") Duration cache
) {
}
