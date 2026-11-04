package br.com.sentinela.infra.security;

import br.com.sentinela.service.MaintenanceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RequiredArgsConstructor
@Component
public class MaintenanceFilter extends OncePerRequestFilter {

    private final MaintenanceService maintenanceService;

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request) {

        String path = request.getRequestURI()
                .substring(request.getContextPath().length());

        return HttpMethod.OPTIONS.matches(request.getMethod())
                || path.equals("/api/system/status")
                || path.equals("/api/connection/probe")
                || path.equals("/actuator/health") || path.startsWith("/actuator/health/")
                || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        if (!maintenanceService.getStatus().maintenance()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.RETRY_AFTER, "30");
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"SYSTEM_MAINTENANCE\",\"message\":\"Sistema em manutenção\"}");
    }
}
