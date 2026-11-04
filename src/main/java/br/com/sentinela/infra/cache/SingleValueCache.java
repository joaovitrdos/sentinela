package br.com.sentinela.infra.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class SingleValueCache<T> {

    private static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(1);

    private final Duration ttl;
    private final Duration maxStale;
    private final Supplier<T> loader;
    private final Predicate<T> cacheable;
    private final Clock clock;

    private T value;
    private Instant expiresAt = Instant.MIN;
    private Instant staleUntil = Instant.MIN;
    private Instant retryAt = Instant.MIN;
    private RuntimeException lastFailure;
    private CompletableFuture<T> inFlight;

    public SingleValueCache(Duration ttl, Supplier<T> loader) {
        this(ttl, ttl.multipliedBy(2), loader, value -> value != null, Clock.systemUTC());
    }

    public SingleValueCache(Duration ttl, Supplier<T> loader, Clock clock) {
        this(ttl, ttl.multipliedBy(2), loader, value -> value != null, clock);
    }

    public SingleValueCache(Duration ttl, Duration maxStale, Supplier<T> loader, Predicate<T> cacheable, Clock clock) {
        this.ttl = ttl;
        this.maxStale = maxStale;
        this.loader = loader;
        this.cacheable = cacheable;
        this.clock = clock;
    }

    public T get() {
        CompletableFuture<T> waitFor = null;
        T stale = null;
        synchronized (this) {
            Instant now = clock.instant();
            if (value != null && now.isBefore(expiresAt)) {
                return value;
            }

            if (value != null && now.isBefore(staleUntil)) {
                stale = value;
                if (now.isBefore(retryAt)) {
                    return stale;
                }
                if (inFlight == null) {
                    startLoad();
                }
                return stale;
            }

            if (lastFailure != null && now.isBefore(retryAt) && inFlight == null) {
                throw lastFailure;
            }
            if (inFlight == null) {
                waitFor = startLoad();
            } else {
                waitFor = inFlight;
            }
        }

        try {
            return waitFor.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw exception;
        }
    }

    private CompletableFuture<T> startLoad() {
        CompletableFuture<T> future = new CompletableFuture<>();
        inFlight = future;
        Thread.startVirtualThread(() -> {
            try {
                T loaded = loader.get();
                if (!cacheable.test(loaded)) {
                    throw new IllegalStateException("Cache loader returned an empty or invalid value");
                }
                synchronized (SingleValueCache.this) {
                    Instant now = clock.instant();
                    value = loaded;
                    expiresAt = now.plus(ttl);
                    staleUntil = now.plus(maxStale);
                    retryAt = expiresAt;
                    lastFailure = null;
                    inFlight = null;
                }
                future.complete(loaded);
            } catch (RuntimeException exception) {
                synchronized (SingleValueCache.this) {
                    lastFailure = exception;
                    retryAt = clock.instant().plus(RETRY_AFTER_FAILURE);
                    inFlight = null;
                    if (value == null || !clock.instant().isBefore(staleUntil)) {
                        value = null;
                    }
                }
                future.completeExceptionally(exception);
            }
        });
        return future;
    }
}
