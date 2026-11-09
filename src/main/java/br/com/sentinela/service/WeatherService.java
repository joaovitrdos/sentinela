package br.com.sentinela.service;

import br.com.sentinela.util.Util;

import br.com.sentinela.dto.response.CurrentWeatherDto;
import br.com.sentinela.dto.response.ForecastWeatherDto;
import br.com.sentinela.dto.response.WeatherTimeZoneDto;
import br.com.sentinela.exception.WeatherApiUnavailableException;
import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

@Service
public class WeatherService {

    private final RestClient currentWeatherRestClient;
    private final RestClient forecastWeatherRestClient;
    private final RestClient timeZoneWeatherRestClient;
    private final String apiKey;

    public WeatherService(
            @Qualifier("weatherCurrentRestClient") RestClient currentWeatherRestClient,
            @Qualifier("weatherForecastRestClient") RestClient forecastWeatherRestClient,
            @Qualifier("weatherTimeZoneRestClient") RestClient timeZoneWeatherRestClient,
            @Value("${weather.api.key}") String apiKey) {

        this.currentWeatherRestClient = currentWeatherRestClient;
        this.forecastWeatherRestClient = forecastWeatherRestClient;
        this.timeZoneWeatherRestClient = timeZoneWeatherRestClient;
        this.apiKey = apiKey;
    }

    public CurrentWeatherDto searchCurrentWeather(
            Double latitude,
            Double longitude) {

        String coordinates = latitude + "," + longitude;

        JsonNode response = requestWeather(() -> currentWeatherRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("key", apiKey)
                        .queryParam("q", coordinates)
                        .queryParam("lang", "pt")
                        .queryParam("aqi", "no")
                        .build())
                .retrieve()
                .body(JsonNode.class));

        if (Util.isNull(response)) {
            throw new IllegalStateException(
                    "Resposta vazia da WeatherAPI");
        }

        JsonNode location = response.path("location");
        JsonNode current = response.path("current");

        return new CurrentWeatherDto(
                location.path("name").asText(),
                location.path("region").asText(),
                location.path("country").asText(),
                location.path("lat").asDouble(),
                location.path("lon").asDouble(),
                current.path("temp_c").asDouble(),
                current.path("feelslike_c").asDouble(),
                current.path("humidity").asInt(),
                current.path("precip_mm").asDouble(),
                current.path("wind_kph").asDouble(),
                current.path("gust_kph").asDouble(),
                current.path("pressure_mb").asDouble(),
                current.path("condition")
                        .path("text").asText(),
                current.path("condition")
                        .path("icon").asText(),
                current.path("last_updated").asText()
        );
    }

    public ForecastWeatherDto searchForecast(
            Double latitude,
            Double longitude,
            Integer days) {

        String coordinates = latitude + "," + longitude;

        JsonNode response = requestWeather(() -> forecastWeatherRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("key", apiKey)
                        .queryParam("q", coordinates)
                        .queryParam("days", days)
                        .queryParam("lang", "pt")
                        .queryParam("aqi", "no")
                        .build())
                .retrieve()
                .body(JsonNode.class));

        if (Util.isNull(response)) {
            throw new IllegalStateException(
                    "Resposta vazia da WeatherAPI");
        }

        return new ForecastWeatherDto(
                response.path("location"),
                response.path("current"),
                response.path("forecast")
        );
    }

    public WeatherTimeZoneDto searchTimeZone(Double latitude, Double longitude) {
        String coordinates = latitude + "," + longitude;
        JsonNode response = requestWeather(() -> timeZoneWeatherRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("key", apiKey)
                        .queryParam("q", coordinates)
                        .build())
                .retrieve()
                .body(JsonNode.class));

        if (Util.isNull(response)) {
            throw new IllegalStateException("Resposta vazia da WeatherAPI time-zone");
        }

        JsonNode location = response.path("location");
        return new WeatherTimeZoneDto(
                location.path("name").asText(),
                location.path("region").asText(),
                location.path("country").asText(),
                location.path("lat").asDouble(),
                location.path("lon").asDouble(),
                location.path("tz_id").asText(),
                location.path("localtime_epoch").asLong(),
                location.path("localtime").asText());
    }

    private JsonNode requestWeather(Supplier<JsonNode> request) {
        try {
            return request.get();
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new WeatherApiUnavailableException();
        }
    }
}
