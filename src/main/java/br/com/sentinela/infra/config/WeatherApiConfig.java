package br.com.sentinela.infra.config;

import br.com.sentinela.service.WeatherApiUsageService;
import br.com.sentinela.util.LogUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class WeatherApiConfig {

    @Bean("weatherCurrentRestClient")
    public RestClient weatherCurrentRestClient(
            RestClient.Builder builder,
            @Value("${weather.api.current-url}") String url,
            WeatherApiUsageService usage) {

        return builder
                .baseUrl(url)
                .requestFactory(requestFactory())
                .requestInterceptor(counting(usage))
                .build();
    }

    @Bean("weatherForecastRestClient")
    public RestClient weatherForecastRestClient(
            RestClient.Builder builder,
            @Value("${weather.api.forecast-url}") String url,
            WeatherApiUsageService usage) {

        return builder
                .baseUrl(url)
                .requestFactory(requestFactory())
                .requestInterceptor(counting(usage))
                .build();
    }

    @Bean("weatherTimeZoneRestClient")
    public RestClient weatherTimeZoneRestClient(
            RestClient.Builder builder,
            @Value("${weather.api.time-zone-url}") String url,
            WeatherApiUsageService usage) {

        return builder
                .baseUrl(url)
                .requestFactory(requestFactory())
                .requestInterceptor(counting(usage))
                .build();
    }

    /** Uma falha na contagem nunca pode impedir a consulta do clima. */
    private ClientHttpRequestInterceptor counting(WeatherApiUsageService usage) {
        return (request, body, execution) -> {
            try {
                usage.record();
            } catch (RuntimeException exception) {
                LogUtil.warn("Não foi possível contar a chamada à WeatherAPI");
            }
            return execution.execute(request, body);
        };
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return factory;
    }

}
