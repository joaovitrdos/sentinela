package br.com.sentinela.infra.security;

import br.com.sentinela.infra.config.SentinelaSecurityProperties;
import com.github.benmanes.caffeine.cache.Cache;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Cache<String, Bucket> buckets;
    private final SentinelaSecurityProperties.RateLimit rateLimit;
    private final TokenService tokenService;
    private final ErrorResponseWriter errorResponseWriter;

    public RateLimitFilter(
            Cache<String, Bucket> rateLimitBuckets,
            SentinelaSecurityProperties securityProperties,
            TokenService tokenService,
            ErrorResponseWriter errorResponseWriter) {

        this.buckets = rateLimitBuckets;
        this.rateLimit = securityProperties.rateLimit();
        this.tokenService = tokenService;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Target target = targetFor(request);
        Bucket bucket = buckets.get(target.key(), key -> newBucket(target.limit()));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (!probe.isConsumed()) {
            long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
            errorResponseWriter.write(response, HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas requisições. Tente novamente em alguns instantes");
            return;
        }

        response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !rateLimit.enabled() || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    private Target targetFor(HttpServletRequest request) {
        String ip = request.getRemoteAddr();

        if (request.getRequestURI().startsWith("/auth")) {
            return new Target("auth:" + ip, rateLimit.auth());
        }

        String email = tokenService.emailFrom(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (email == null) {
            return new Target("ip:" + ip, rateLimit.anonymous());
        }
        return new Target("user:" + email, rateLimit.user());
    }

    private Bucket newBucket(SentinelaSecurityProperties.Limit limit) {
        return Bucket.builder()
                .addLimit(bandwidth -> bandwidth
                        .capacity(limit.capacity())
                        .refillGreedy(limit.capacity(), limit.window()))
                .build();
    }

    private record Target(String key, SentinelaSecurityProperties.Limit limit) {
    }
}
