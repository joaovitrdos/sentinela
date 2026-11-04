package br.com.sentinela.controller;

import br.com.sentinela.dto.response.SystemStatusDto;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SystemControllerTest extends WebLayerTestSupport {
    @Test
    void statusIsPublicAndNotCached() throws Exception {
        mockMvc.perform(get("/api/system/status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.maintenance").value(false))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void statusStaysPublicWhileOtherRoutesReturn503() throws Exception {
        when(maintenanceService.getStatus()).thenReturn(new SystemStatusDto(true,"test",null,null,"America/Sao_Paulo"));
        mockMvc.perform(get("/api/system/status")).andExpect(status().isOk()).andExpect(jsonPath("$.maintenance").value(true));
        mockMvc.perform(post("/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SYSTEM_MAINTENANCE"));
    }

    @Test
    void normalAuthenticationRemainsRequired() throws Exception {
        mockMvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
    }
}
