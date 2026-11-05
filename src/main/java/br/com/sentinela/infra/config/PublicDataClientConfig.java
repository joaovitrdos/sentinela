package br.com.sentinela.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Clientes das fontes públicas e gratuitas, que não exigem chave de acesso. */
@Configuration
public class PublicDataClientConfig {

    @Bean("inmetRestClient")
    public RestClient inmetRestClient(RestClient.Builder builder, @Value("${public-data.inmet-warnings-url}") String url) {
        return client(builder, url);
    }

    @Bean("nasaPowerRestClient")
    public RestClient nasaPowerRestClient(RestClient.Builder builder, @Value("${public-data.nasa-power-url}") String url) {
        return client(builder, url);
    }

    @Bean("carRestClient")
    public RestClient carRestClient(RestClient.Builder builder, @Value("${public-data.car-wfs-url}") String url) {
        return client(builder, url);
    }

    private RestClient client(RestClient.Builder builder, String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return builder.baseUrl(url).defaultHeader(HttpHeaders.USER_AGENT, "Sentinela/1.0").requestFactory(factory).build();
    }
}
