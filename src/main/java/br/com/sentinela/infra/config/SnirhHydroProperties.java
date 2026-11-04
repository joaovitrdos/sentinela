package br.com.sentinela.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Settings for SNIRH reference levels, station inventory, and main river layers. */
@ConfigurationProperties(prefix = "snirh.hydro")
public record SnirhHydroProperties(
        String levelsUrl,
        String stationsUrl,
        String riversUrl,
        @DefaultValue("1000") int pageSize,
        @DefaultValue("6") int parallelPages,
        @DefaultValue("4") int maxRetries,
        @DefaultValue("2000") long retryDelayMs,
        @DefaultValue("0.01") double riverSimplificationDegrees,
        @DefaultValue("10m") Duration levelsCache,
        @DefaultValue("12h") Duration stationsCache,
        @DefaultValue("7d") Duration riversCache,
        @DefaultValue("true") boolean preload
) {
}
