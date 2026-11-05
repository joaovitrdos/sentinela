package br.com.sentinela.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.sentinela.entity.User;
import br.com.sentinela.enums.Feature;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class PlanAccessControllerTest extends WebLayerTestSupport {

    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final UUID PLOT_ID = UUID.randomUUID();

    @Test
    void featureOutsideThePlanIsBlockedBeforeReachingTheService() throws Exception {
        User user = user();
        when(planService.allows(user.getUserId(), PROPERTY_ID, Feature.IRRIGATION)).thenReturn(false);

        mockMvc.perform(get("/api/properties/{id}/plots/{plot}/irrigation-recommendation", PROPERTY_ID, PLOT_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());

        verify(irrigationService, never()).recommend(any(), any(), any());
    }

    @Test
    void routesWithoutPropertyUseTheUserOwnPlan() throws Exception {
        User user = user();
        when(planService.allows(user.getUserId(), null, Feature.BASE)).thenReturn(false);

        mockMvc.perform(get("/api/hydro/levels").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void withoutPlanReadingIsAllowedButWritingIsNot() throws Exception {
        User user = user();
        String token = bearer(user);
        when(planService.allows(eq(user.getUserId()), any(), eq(Feature.BASE))).thenReturn(false);

        mockMvc.perform(get("/api/properties/{id}/plots", PROPERTY_ID).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/properties/{id}/plots", PROPERTY_ID).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Talhão\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void withoutPlanUserCanStillSubscribeAndLeaveTeam() throws Exception {
        User user = user();
        String token = bearer(user);
        when(planService.allows(any(), any(), any(Feature.class))).thenReturn(false);

        mockMvc.perform(get("/api/billing/me").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
        mockMvc.perform(post("/api/billing/subscribe").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"NORMAL\",\"cycle\":\"MONTHLY\",\"cpfCnpj\":\"12345678909\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/properties/{id}/team/me", PROPERTY_ID).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
    }

    @Test
    void plansAreVisibleWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/plans")).andExpect(status().isOk());
    }

    @Test
    void webhookIsReachableWithoutLoginAndReceivesTheToken() throws Exception {
        mockMvc.perform(post("/api/billing/webhooks/asaas").header("asaas-access-token", "abc")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"evt_1\",\"event\":\"PAYMENT_RECEIVED\"}"))
                .andExpect(status().isOk());

        verify(billingService).handleWebhook(eq("abc"), any());
    }

    @Test
    void otherBillingRoutesRequireLogin() throws Exception {
        mockMvc.perform(get("/api/billing/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/billing/cancel")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSubscribeRequestIsRejected() throws Exception {
        User user = user();

        mockMvc.perform(post("/api/billing/subscribe").header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"planCode\":\"NORMAL\",\"cycle\":\"SEMANAL\"}"))
                .andExpect(status().isBadRequest());

        verify(billingService, never()).subscribe(any(), any());
    }
}
