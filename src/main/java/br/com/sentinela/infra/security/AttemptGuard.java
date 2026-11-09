package br.com.sentinela.infra.security;

import br.com.sentinela.util.Util;

import br.com.sentinela.exception.TooManyCodeAttemptsException;
import br.com.sentinela.exception.TooManyCodeRequestsException;
import br.com.sentinela.exception.TooManyLoginAttemptsException;
import br.com.sentinela.infra.config.SentinelaSecurityProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AttemptGuard {

    private final SentinelaSecurityProperties.Attempts attempts;
    private final Cache<String, AtomicInteger> loginFailures;
    private final Cache<String, AtomicInteger> codeRequests;
    private final Cache<String, AtomicInteger> codeAttempts;

    public AttemptGuard(SentinelaSecurityProperties securityProperties) {
        this.attempts = securityProperties.attempts();
        this.loginFailures = counters(attempts.loginLockout());
        this.codeRequests = counters(attempts.recoveryWindow());
        this.codeAttempts = counters(attempts.codeWindow());
    }

    public void checkLogin(String email) {
        if (current(loginFailures, email) >= attempts.maxLoginFailures()) {
            throw new TooManyLoginAttemptsException();
        }
    }

    public void recordLoginFailure(String email) {
        increment(loginFailures, email);
    }

    public void clearLogin(String email) {
        loginFailures.invalidate(email);
    }

    public void checkRecovery(String email) {
        if (increment(codeRequests, email) > attempts.maxRecoveryRequests()) {
            throw new TooManyCodeRequestsException();
        }
    }

    public void checkRecovery(String email, String clientAddress) {
        checkRecovery(clientKey(email, clientAddress));
    }

    public void checkCodeAttempt(String email) {
        if (increment(codeAttempts, email) > attempts.maxCodeAttempts()) {
            throw new TooManyCodeAttemptsException();
        }
    }

    public void checkCodeAttempt(String email, String clientAddress) {
        checkCodeAttempt(clientKey(email, clientAddress));
    }

    public void clearCodeAttempts(String email) {
        codeAttempts.invalidate(email);
    }

    public void clearCodeAttempts(String email, String clientAddress) {
        codeAttempts.invalidate(clientKey(email, clientAddress));
    }

    private static Cache<String, AtomicInteger> counters(Duration window) {
        return Caffeine.newBuilder()
                .expireAfterWrite(window)
                .maximumSize(100_000)
                .build();
    }

    private int current(Cache<String, AtomicInteger> counters, String email) {
        AtomicInteger counter = counters.getIfPresent(email);
        return Util.isNull(counter) ? 0 : counter.get();
    }

    private int increment(Cache<String, AtomicInteger> counters, String email) {
        return counters.get(email, key -> new AtomicInteger()).incrementAndGet();
    }

    private String clientKey(String email, String clientAddress) {
        return email + "@" + (Util.isNullOrBlank(clientAddress) ? "unknown" : clientAddress);
    }
}
