package br.com.sentinela.infra.security;

import br.com.sentinela.infra.config.SentinelaSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private final TokenService tokenService = mock(TokenService.class);

    private RateLimitFilter filter(boolean enabled, long authCapacity, long userCapacity) {
        SentinelaSecurityProperties.Limit window = new SentinelaSecurityProperties.Limit(authCapacity, Duration.ofMinutes(1));
        SentinelaSecurityProperties properties = new SentinelaSecurityProperties(
                new SentinelaSecurityProperties.RateLimit(enabled, window, window,
                        new SentinelaSecurityProperties.Limit(userCapacity, Duration.ofMinutes(1))),
                null);
        return new RateLimitFilter(SecurityConfig.rateLimitBuckets(), properties, tokenService, new ErrorResponseWriter());
    }

    private MockHttpServletRequest request(String method, String path, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        request.setRemoteAddr(ip);
        return request;
    }

    /** Executa o filtro e devolve a resposta; chain.getRequest() fica nulo quando o filtro barra a requisicao. */
    private Result call(RateLimitFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Result(response, chain.getRequest() != null);
    }

    private record Result(MockHttpServletResponse response, boolean passedThrough) {
    }

    @Test
    void deixaPassarAteOLimiteEBloqueiaDepoisCom429() throws Exception {
        RateLimitFilter filter = filter(true, 2, 100);

        Result first = call(filter, request("POST", "/auth/login", "10.0.0.1"));
        Result second = call(filter, request("POST", "/auth/login", "10.0.0.1"));
        Result third = call(filter, request("POST", "/auth/login", "10.0.0.1"));

        assertThat(first.passedThrough()).isTrue();
        assertThat(first.response().getHeader("X-RateLimit-Remaining")).isEqualTo("1");
        assertThat(second.passedThrough()).isTrue();
        assertThat(third.passedThrough()).isFalse();
        assertThat(third.response().getStatus()).isEqualTo(429);
        assertThat(third.response().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(third.response().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("Muitas requisições. Tente novamente em alguns instantes");
    }

    @Test
    void cadaIpTemOProprioLimite() throws Exception {
        RateLimitFilter filter = filter(true, 1, 100);

        call(filter, request("POST", "/auth/login", "10.0.0.1"));
        Result otherIp = call(filter, request("POST", "/auth/login", "10.0.0.2"));

        assertThat(otherIp.passedThrough()).isTrue();
    }

    @Test
    void usuarioLogadoUsaOLimiteDeUsuarioPorId() throws Exception {
        RateLimitFilter filter = filter(true, 1, 3);
        when(tokenService.subjectFrom("Bearer token")).thenReturn("user-123");

        int passed = 0;
        for (int i = 0; i < 4; i++) {
            MockHttpServletRequest request = request("GET", "/users/me", "10.0.0.1");
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
            if (call(filter, request).passedThrough()) {
                passed++;
            }
        }

        assertThat(passed).isEqualTo(3);
    }

    @Test
    void preflightOptionsNaoEhLimitado() throws Exception {
        RateLimitFilter filter = filter(true, 1, 1);

        call(filter, request("OPTIONS", "/auth/login", "10.0.0.1"));
        Result second = call(filter, request("OPTIONS", "/auth/login", "10.0.0.1"));

        assertThat(second.passedThrough()).isTrue();
    }

    @Test
    void desligadoNaoLimitaNada() throws Exception {
        RateLimitFilter filter = filter(false, 1, 1);

        call(filter, request("POST", "/auth/login", "10.0.0.1"));
        Result second = call(filter, request("POST", "/auth/login", "10.0.0.1"));

        assertThat(second.passedThrough()).isTrue();
    }
}
