package br.com.sentinela.infra.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({SnirhProperties.class, SnirhHydroProperties.class})
public class SnirhClientConfig {

    @Bean
    public RestClient snirhRestClient(RestClient.Builder builder) {
        // Without timeouts, a stalled SNIRH page could block the user request indefinitely.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        return builder.requestFactory(requestFactory).build();
    }
}
