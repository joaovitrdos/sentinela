package br.com.sentinela.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sentinela.entity.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class FieldAdvisoryControllerTest extends WebLayerTestSupport {

    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final UUID PLOT_ID = UUID.randomUUID();

    @Test
    void newEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/properties/{id}/spray-windows", PROPERTY_ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/properties/{id}/plots/{plot}/irrigation-recommendation", PROPERTY_ID, PLOT_ID))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/properties/{id}/alerts", PROPERTY_ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/car/{code}", "GO-5207808-0530A0B57A184968987F8FE2DBA313ED")).andExpect(status().isUnauthorized());
    }

    @Test
    void routesReachTheirServices() throws Exception {
        User user = user();
        String token = bearer(user);

        mockMvc.perform(get("/api/properties/{id}/spray-windows", PROPERTY_ID).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/properties/{id}/plots/{plot}/irrigation-recommendation", PROPERTY_ID, PLOT_ID)
                .header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
        mockMvc.perform(get("/api/car/{code}", "GO-5207808-0530.A0B5.7A18.4968.987F.8FE2.DBA3.13ED")
                .header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());

        verify(sprayWindowService).windows(user.getUserId(), PROPERTY_ID);
        verify(irrigationService).recommend(user.getUserId(), PROPERTY_ID, PLOT_ID);
        verify(carService).find("GO-5207808-0530.A0B5.7A18.4968.987F.8FE2.DBA3.13ED");
    }

    @Test
    void alertSettingsRejectValuesOutOfRange() throws Exception {
        User user = user();

        mockMvc.perform(put("/api/properties/{id}/alerts/settings", PROPERTY_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"frostTemperatureC\":50,\"heavyRainMm\":50,\"ndviDrop\":0.15,\"riverRadiusKm\":30}"))
                .andExpect(status().isBadRequest());

        verify(propertyAlertService, never()).updateSettings(any(), eq(PROPERTY_ID), any());
    }
}
