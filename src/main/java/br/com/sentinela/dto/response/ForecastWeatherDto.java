package br.com.sentinela.dto.response;

import tools.jackson.databind.JsonNode;

public record ForecastWeatherDto(
        JsonNode location,
        JsonNode current,
        JsonNode forecast
) {
}
