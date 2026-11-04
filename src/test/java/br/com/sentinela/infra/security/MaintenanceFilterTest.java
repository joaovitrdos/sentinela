package br.com.sentinela.infra.security;

import br.com.sentinela.dto.response.SystemStatusDto;
import br.com.sentinela.service.MaintenanceService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MaintenanceFilterTest {
    private final MaintenanceService service = mock(MaintenanceService.class);
    private final MaintenanceFilter filter = new MaintenanceFilter(service);

    private MockHttpServletResponse call(String method, String path, boolean active) throws Exception {
        when(service.getStatus()).thenReturn(new SystemStatusDto(active, "test", null, null, "America/Sao_Paulo"));
        var request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        request.addHeader("Accept", "text/html,application/json");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void returnsMachineReadable503RatherThanRedirectingToMissingHtml() throws Exception {
        var response = call("GET", "/api/weather/current", true);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("SYSTEM_MAINTENANCE");
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(response.getHeader("Retry-After")).isEqualTo("30");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void availableSystemPassesThrough() throws Exception {
        assertThat(call("GET", "/users/me", false).getStatus()).isEqualTo(200);
    }

    @Test
    void statusRemainsAvailableDuringMaintenance() throws Exception {
        assertThat(call("GET", "/api/system/status", true).getStatus()).isEqualTo(200);
        verify(service, never()).getStatus();
    }

    @Test
    void healthChecksRemainAvailable() throws Exception {
        assertThat(call("GET", "/actuator/health/readiness", true).getStatus()).isEqualTo(200);
        verify(service, never()).getStatus();
    }

    @Test
    void preflightIsNotBlocked() throws Exception {
        assertThat(call("OPTIONS", "/api/weather/current", true).getStatus()).isEqualTo(200);
        verify(service, never()).getStatus();
    }
}
