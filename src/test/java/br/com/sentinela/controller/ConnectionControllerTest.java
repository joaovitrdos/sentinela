package br.com.sentinela.controller;

import br.com.sentinela.dto.response.SystemStatusDto;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConnectionControllerTest extends WebLayerTestSupport {

    @Test
    void probeIsPublicAndNotCachedDuringMaintenance() throws Exception {
        when(maintenanceService.getStatus()).thenReturn(new SystemStatusDto(true, "test", null, null, "America/Sao_Paulo"));
        mockMvc.perform(head("/api/connection/probe"))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(get("/api/connection/probe")).andExpect(status().isNoContent());
    }

    @Test
    void acceptsOnlyValidConnectionStatus() throws Exception {
        mockMvc.perform(post("/api/connection/status").contentType("application/json")
                .content("{\"state\":\"GOOD\",\"previousState\":\"POOR\",\"latencyMs\":25}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/connection/status").contentType("application/json")
                .content("{\"state\":\"GOOD\",\"latencyMs\":-2}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/connection/status").contentType("application/json")
                .content("{\"state\":\"UNKNOWN\",\"latencyMs\":5}"))
                .andExpect(status().isBadRequest());
    }
}
