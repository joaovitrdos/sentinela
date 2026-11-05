package br.com.sentinela.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class AsaasConfig {

    /** O Asaas recomenda ao menos 60 segundos de espera para evitar cobranças duplicadas em novas tentativas. */
    @Bean("asaasRestClient")
    public RestClient asaasRestClient(RestClient.Builder builder, @Value("${asaas.base-url}") String url,
                                      @Value("${asaas.api-key:}") String apiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return builder.baseUrl(url).requestFactory(factory).defaultHeader("access_token", apiKey)
                .defaultHeader(HttpHeaders.USER_AGENT, "Sentinela/1.0").build();
    }
}
