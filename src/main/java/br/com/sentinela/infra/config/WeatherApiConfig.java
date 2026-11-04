package br.com.sentinela.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class WeatherApiConfig {

    @Bean("weatherCurrentRestClient")
    public RestClient weatherCurrentRestClient(
            RestClient.Builder builder,
            @Value("${weather.api.current-url}") String url) {

        return builder
                .baseUrl(url)
                .build();
    }

    @Bean("weatherForecastRestClient")
    public RestClient weatherForecastRestClient(
            RestClient.Builder builder,
            @Value("${weather.api.forecast-url}") String url) {

        return builder
                .baseUrl(url)
                .build();
    }

    @Bean("weatherTimeZoneRestClient")
    public RestClient weatherTimeZoneRestClient(
            RestClient.Builder builder,
            @Value("${weather.api.time-zone-url}") String url) {

        return builder
                .baseUrl(url)
                .build();
    }

}
