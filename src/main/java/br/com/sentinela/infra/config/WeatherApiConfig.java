package br.com.sentinela.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class WeatherApiConfig {

    @Bean("weatherCurrentRestClient")
    public RestClient weatherCurrentRestClient(
            RestClient.Builder builder,
            @Value("${weather.api.current-url}") String url) {

        return builder
                .baseUrl(url)
                .requestFactory(requestFactory())
                .build();
    }

    @Bean("weatherForecastRestClient")
    public RestClient weatherForecastRestClient(
            RestClient.Builder builder,
            @Value("${weather.api.forecast-url}") String url) {

        return builder
                .baseUrl(url)
                .requestFactory(requestFactory())
                .build();
    }

    @Bean("weatherTimeZoneRestClient")
    public RestClient weatherTimeZoneRestClient(
            RestClient.Builder builder,
            @Value("${weather.api.time-zone-url}") String url) {

        return builder
                .baseUrl(url)
                .requestFactory(requestFactory())
                .build();
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return factory;
    }

}
