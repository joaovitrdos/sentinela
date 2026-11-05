package br.com.sentinela.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.sentinela.infra.client.asaas.AsaasClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

class AsaasClientTest {

    private static final String BASE = "https://api-sandbox.asaas.com/v3";

    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE).defaultHeader("access_token", "chave");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final AsaasClient client = new AsaasClient(builder.build(), JsonMapper.builder().build(), "chave");

    @Test
    void createsSubscriptionLettingTheCustomerChooseThePaymentMethod() {
        server.expect(requestTo(BASE + "/subscriptions")).andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", "chave"))
                .andExpect(content().json("""
                        {"customer":"cus_1","billingType":"UNDEFINED","value":60.00,"cycle":"MONTHLY",
                         "nextDueDate":"2026-11-12","externalReference":"usuario"}"""))
                .andRespond(withSuccess("{\"id\":\"sub_1\"}", MediaType.APPLICATION_JSON));

        String id = client.createSubscription("cus_1", new BigDecimal("60.00"), "MONTHLY", LocalDate.of(2026, 11, 12), "Plano", "usuario");

        assertThat(id).isEqualTo("sub_1");
        server.verify();
    }

    @Test
    void findsCustomerByTheSentinelaIdentifier() {
        server.expect(requestTo(BASE + "/customers?externalReference=usuario"))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"cus_1\"}]}", MediaType.APPLICATION_JSON));
        assertThat(client.findCustomerId("usuario")).isEqualTo("cus_1");
    }

    @Test
    void missingCustomerReturnsNull() {
        server.expect(requestTo(BASE + "/customers?externalReference=usuario"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        assertThat(client.findCustomerId("usuario")).isNull();
    }

    @Test
    void removesEveryActiveRecurrenceOfTheUser() {
        server.expect(requestTo(BASE + "/subscriptions?externalReference=usuario&status=ACTIVE"))
                .andRespond(withSuccess("{\"data\":[{\"id\":\"sub_1\"},{\"id\":\"sub_2\"}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/subscriptions/sub_1")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"deleted\":true}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/subscriptions/sub_2")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"deleted\":true}", MediaType.APPLICATION_JSON));

        client.deleteActiveSubscriptions("usuario");

        server.verify();
    }

    @Test
    void readsTheInvoiceOfTheFirstCharge() {
        server.expect(requestTo(BASE + "/subscriptions/sub_1/payments"))
                .andRespond(withSuccess("{\"data\":[{\"invoiceUrl\":\"https://fatura\"}]}", MediaType.APPLICATION_JSON));
        assertThat(client.firstInvoiceUrl("sub_1")).isEqualTo("https://fatura");
    }

    @Test
    void validationErrorShowsTheAsaasMessage() {
        server.expect(requestTo(BASE + "/customers")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"errors\":[{\"description\":\"CPF inválido\"}]}"));

        assertThatThrownBy(() -> client.createCustomer("Ana", "a@b.c", "1", "u")).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(exception.getReason()).isEqualTo("CPF inválido");
        });
    }

    @Test
    void invalidKeyDoesNotExposeTheProviderError() {
        server.expect(requestTo(BASE + "/customers?externalReference=u")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.findCustomerId("u")).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void missingKeyFailsWithoutCallingTheProvider() {
        AsaasClient unconfigured = new AsaasClient(builder.build(), JsonMapper.builder().build(), "");

        assertThatThrownBy(() -> unconfigured.findCustomerId("u")).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getReason()).isEqualTo("Pagamentos ainda não configurados"));
    }
}
