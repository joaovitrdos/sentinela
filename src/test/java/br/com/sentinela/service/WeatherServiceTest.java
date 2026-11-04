package br.com.sentinela.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WeatherServiceTest {

    private static final String RESPONSE = """
            {
              "location": {
                "name": "Colatina", "region": "Espirito Santo", "country": "Brazil",
                "lat": -19.54, "lon": -40.63, "tz_id": "America/Sao_Paulo",
                "localtime_epoch": 1793581200, "localtime": "2026-11-02 03:20"
              },
              "current": {
                "temp_c": 24.5, "feelslike_c": 26.0, "humidity": 80, "precip_mm": 1.5,
                "wind_kph": 12.0, "gust_kph": 18.0, "pressure_mb": 1013.0,
                "condition": {"text": "Parcialmente nublado", "icon": "//cdn.weatherapi.com/weather/64x64/day/116.png"},
                "last_updated": "2026-11-02 03:15"
              },
              "forecast": {
                "forecastday": [{"date": "2026-11-02", "day": {"totalprecip_mm": 8.5}, "hour": []}]
              }
            }
            """;

    @Test
    void readsCurrentWeatherWithTheDefaultJsonConverter() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.baseUrl("https://weather.test/v1/current.json").build();
        server.expect(requestTo("https://weather.test/v1/current.json?key=test-key&q=-19.54,-40.63&lang=pt&aqi=no"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        var weather = service(client).searchCurrentWeather(-19.54, -40.63);

        assertThat(weather.city()).isEqualTo("Colatina");
        assertThat(weather.temperature()).isEqualTo(24.5);
        assertThat(weather.precipitation()).isEqualTo(1.5);
        assertThat(weather.condition()).isEqualTo("Parcialmente nublado");
        server.verify();
    }

    @Test
    void readsAndSerializesTheForecastAsNestedJson() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.baseUrl("https://weather.test/v1/forecast.json").build();
        server.expect(requestTo("https://weather.test/v1/forecast.json?key=test-key&q=-19.54,-40.63&days=3&lang=pt&aqi=no"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        var forecast = service(client).searchForecast(-19.54, -40.63, 3);
        JsonMapper mapper = JsonMapper.builder().build();
        var json = mapper.readTree(mapper.writeValueAsString(forecast));

        assertThat(json.path("location").path("name").asString()).isEqualTo("Colatina");
        assertThat(json.path("current").path("temp_c").asDouble()).isEqualTo(24.5);
        assertThat(json.path("forecast").path("forecastday").get(0).path("day").path("totalprecip_mm").asDouble())
                .isEqualTo(8.5);
        server.verify();
    }

    @Test
    void readsTimeZoneWithTheDefaultJsonConverter() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.baseUrl("https://weather.test/v1/timezone.json").build();
        server.expect(requestTo("https://weather.test/v1/timezone.json?key=test-key&q=-19.54,-40.63"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        var timeZone = service(client).searchTimeZone(-19.54, -40.63);

        assertThat(timeZone.city()).isEqualTo("Colatina");
        assertThat(timeZone.timeZone()).isEqualTo("America/Sao_Paulo");
        assertThat(timeZone.localTimeEpoch()).isEqualTo(1793581200L);
        assertThat(timeZone.localTime()).isEqualTo("2026-11-02 03:20");
        server.verify();
    }

    private WeatherService service(RestClient client) {
        return new WeatherService(client, client, client, "test-key");
    }
}
