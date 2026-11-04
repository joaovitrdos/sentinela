package br.com.sentinela.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "sentinela.security")
public record SentinelaSecurityProperties(
        @DefaultValue RateLimit rateLimit,
        @DefaultValue Attempts attempts
) {

    public record RateLimit(
            @DefaultValue("true") boolean enabled,
            Limit auth,
            Limit anonymous,
            Limit user
    ) {
    }

    public record Limit(long capacity, Duration window) {
    }

    public record Attempts(
            @DefaultValue("5") int maxLoginFailures,
            @DefaultValue("15m") Duration loginLockout,
            @DefaultValue("5") int maxRecoveryRequests,
            @DefaultValue("1h") Duration recoveryWindow,
            @DefaultValue("5") int maxCodeAttempts,
            @DefaultValue("15m") Duration codeWindow
    ) {
    }
}
