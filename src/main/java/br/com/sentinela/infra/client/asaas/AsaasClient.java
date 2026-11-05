package br.com.sentinela.infra.client.asaas;

import br.com.sentinela.util.LogUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class AsaasClient {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final boolean configured;

    public AsaasClient(@Qualifier("asaasRestClient") RestClient client, ObjectMapper mapper, @Value("${asaas.api-key:}") String apiKey) {
        this.client = client;
        this.mapper = mapper;
        this.configured = !apiKey.isBlank();
    }

    /** A API aceita clientes duplicados, por isso a busca pelo identificador do Sentinela vem antes da criação. */
    public String findCustomerId(String externalReference) {
        JsonNode response = call(() -> client.get()
                .uri(uri -> uri.path("/customers").queryParam("externalReference", externalReference).build())
                .retrieve().body(JsonNode.class));
        return response.path("data").path(0).path("id").asText(null);
    }

    public String createCustomer(String name, String email, String cpfCnpj, String externalReference) {
        Map<String, Object> body = Map.of("name", name, "email", email, "cpfCnpj", cpfCnpj, "externalReference", externalReference);
        return post("/customers", body).path("id").asText();
    }

    /** Também encerra assinaturas órfãs, deixadas por uma falha entre a criação no Asaas e a gravação local. */
    public void deleteActiveSubscriptions(String externalReference) {
        JsonNode response = call(() -> client.get()
                .uri(uri -> uri.path("/subscriptions").queryParam("externalReference", externalReference)
                        .queryParam("status", "ACTIVE").build())
                .retrieve().body(JsonNode.class));
        for (JsonNode subscription : response.path("data")) {
            String id = subscription.path("id").asText();
            call(() -> client.delete().uri("/subscriptions/{id}", id).retrieve().body(JsonNode.class));
        }
    }

    /** UNDEFINED deixa o cliente escolher cartão, Pix ou boleto na fatura, sem o cartão passar pelo Sentinela. */
    public String createSubscription(String customerId, BigDecimal value, String cycle, LocalDate nextDueDate,
                                     String description, String externalReference) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customer", customerId);
        body.put("billingType", "UNDEFINED");
        body.put("value", value);
        body.put("cycle", cycle);
        body.put("nextDueDate", nextDueDate.toString());
        body.put("description", description);
        body.put("externalReference", externalReference);
        return post("/subscriptions", body).path("id").asText();
    }

    public String firstInvoiceUrl(String subscriptionId) {
        JsonNode response = call(() -> client.get().uri("/subscriptions/{id}/payments", subscriptionId)
                .retrieve().body(JsonNode.class));
        return response.path("data").path(0).path("invoiceUrl").asText(null);
    }

    private JsonNode post(String path, Map<String, Object> body) {
        return call(() -> client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().body(JsonNode.class));
    }

    private JsonNode call(Supplier<JsonNode> request) {
        if (!configured) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Pagamentos ainda não configurados");
        }
        try {
            JsonNode response = request.get();
            return response == null ? mapper.createObjectNode() : response;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 400 || status == 404 || status == 422) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, description(exception));
            }
            LogUtil.warn("Asaas recusou a chamada com status " + status);
            throw unavailable();
        } catch (RestClientException exception) {
            LogUtil.warn("Asaas indisponível");
            throw unavailable();
        }
    }

    private String description(RestClientResponseException exception) {
        try {
            return mapper.readTree(exception.getResponseBodyAsString()).path("errors").path(0).path("description")
                    .asText("Não foi possível processar o pagamento");
        } catch (RuntimeException invalidBody) {
            return "Não foi possível processar o pagamento";
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Pagamentos indisponíveis. Tente novamente em instantes");
    }
}
