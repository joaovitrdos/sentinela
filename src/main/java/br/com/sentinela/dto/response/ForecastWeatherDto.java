package br.com.sentinela.dto.response;

import br.com.sentinela.util.WeatherAttribution;
import tools.jackson.databind.JsonNode;

public record ForecastWeatherDto(
        JsonNode location,
        JsonNode current,
        JsonNode forecast,
        String source,
        String disclaimer
) {
    public ForecastWeatherDto(JsonNode location, JsonNode current, JsonNode forecast) {
        this(location, current, forecast, WeatherAttribution.SOURCE, WeatherAttribution.DISCLAIMER);
    }
}
