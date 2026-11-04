package br.com.sentinela.infra.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public class SingleValueCache<T> {

    private static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(1);

    private final Duration ttl;
    private final Supplier<T> loader;
    private final Clock clock;

    private T value;
    private Instant expiresAt = Instant.MIN;

    public SingleValueCache(Duration ttl, Supplier<T> loader) {
        this(ttl, loader, Clock.systemUTC());
    }

    public SingleValueCache(Duration ttl, Supplier<T> loader, Clock clock) {
        this.ttl = ttl;
        this.loader = loader;
        this.clock = clock;
    }

    public synchronized T get() {
        Instant now = clock.instant();
        if (value != null && now.isBefore(expiresAt)) {
            return value;
        }
        try {
            value = loader.get();
            expiresAt = now.plus(ttl);
        } catch (RuntimeException exception) {
            if (value == null) {
                throw exception;
            }
            expiresAt = now.plus(RETRY_AFTER_FAILURE);
        }
        return value;
    }
}
