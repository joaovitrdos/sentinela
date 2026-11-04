package br.com.sentinela.controller;

import br.com.sentinela.dto.response.HydroStationResponseDto;
import br.com.sentinela.dto.response.RainReadingResponseDto;
import br.com.sentinela.dto.response.RiverLevelResponseDto;
import br.com.sentinela.dto.response.RiverNetworkResponseDto;
import br.com.sentinela.dto.response.RiverNetworkResponseDto.RiverFeatureDto;
import br.com.sentinela.exception.SnirhApiException;
import br.com.sentinela.exception.SnirhUnavailableException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HydroControllerTest extends WebLayerTestSupport {

    @Test
    void dadosHidrologicosExigemLogin() throws Exception {
        for (String path : List.of("/api/hydro/rain/24h", "/api/hydro/levels", "/api/hydro/stations", "/api/hydro/rivers")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(UNAUTHORIZED));
        }
        verifyNoInteractions(rainService, referenceLevelService, stationService, riverService);
    }

    @Test
    void chuvaVemComDataEmIso() throws Exception {
        when(rainService.getRain24h()).thenReturn(List.of(new RainReadingResponseDto(
                10L, "EST", "BELÉM", "PARÁ", "AMAZONAS", "ANA", 12.4, "Chuva Moderada",
                Instant.parse("2026-10-22T09:00:00Z").atOffset(ZoneOffset.UTC), 3.0, -1.4, -48.5, false)));

        mockMvc.perform(get("/api/hydro/rain/24h").header("Authorization", bearer(user())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value(10))
                .andExpect(jsonPath("$[0].chuvaMm24h").value(12.4))
                .andExpect(jsonPath("$[0].classe").value("Chuva Moderada"))
                .andExpect(jsonPath("$[0].dataUltimoDado").value("2026-10-22T06:00:00-03:00"))
                .andExpect(jsonPath("$[0].requiresAttention").value(false))
                .andExpect(jsonPath("$[0].latitude").value(-1.4));
    }

    @Test
    void niveisEEstacoesVemNoFormatoQueOFrontEspera() throws Exception {
        when(referenceLevelService.getRiverLevels()).thenReturn(List.of(new RiverLevelResponseDto(
                1L, "RIO X", null, "PARÁ", null, null, null, "SGB-CPRM", "RHN", "ATIVA", 315.0, "NORMAL",
                "Normal", Instant.parse("2026-10-22T09:00:00Z").atOffset(ZoneOffset.UTC), 3.0, -3.0, -50.0, false)));
        when(stationService.getStations()).thenReturn(List.of(new HydroStationResponseDto(
                47001L, "MARACANÃ", "FLUVIOMETRICA", true, false, null, "PARÁ", null, null, null, "ANA", null,
                null, null, -0.83, -47.5)));
        String token = bearer(user());

        mockMvc.perform(get("/api/hydro/levels").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("NORMAL"))
                .andExpect(jsonPath("$[0].situacaoEstacao").value("ATIVA"))
                .andExpect(jsonPath("$[0].requiresAttention").value(false))
                .andExpect(jsonPath("$[0].cotaCm").value(315.0));

        mockMvc.perform(get("/api/hydro/stations").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("FLUVIOMETRICA"))
                .andExpect(jsonPath("$[0].operando").value(true))
                .andExpect(jsonPath("$[0].telemetrica").value(false));
    }

    @Test
    void riosVemEmGeoJsonComCacheNoNavegador() throws Exception {
        RiverFeatureDto river = RiverFeatureDto.of(7L, "Rio Negro", 25,
                new double[][][]{{{-60.0, -3.1}, {-60.2, -3.0}}});
        when(riverService.getRivers()).thenReturn(RiverNetworkResponseDto.of(List.of(river)));

        mockMvc.perform(get("/api/hydro/rivers").header("Authorization", bearer(user())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=86400")))
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features[0].properties.nome").value("Rio Negro"))
                .andExpect(jsonPath("$.features[0].properties.km").value(25))
                .andExpect(jsonPath("$.features[0].geometry.type").value("MultiLineString"))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0][1][0]").value(-60.2));
    }

    @Test
    void snirhForaDoArRespondeServiceUnavailable() throws Exception {
        when(stationService.getStations()).thenThrow(new SnirhUnavailableException("timeout"));

        mockMvc.perform(get("/api/hydro/stations").header("Authorization", bearer(user())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("temporariamente indisponíveis")));
    }

    @Test
    void erroDoArcGisRespondeBadGatewaySemVazarDetalhes() throws Exception {
        when(referenceLevelService.getRiverLevels()).thenThrow(new SnirhApiException("Erro ArcGIS 400: Invalid query"));

        mockMvc.perform(get("/api/hydro/levels").header("Authorization", bearer(user())))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(containsString("vieram com erro")));
    }
}
