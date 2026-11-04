package br.com.sentinela.dto.response;

public record CurrentWeatherDto(
        String city,
        String region,
        String country,
        Double latitude,
        Double longitude,
        Double temperature,
        Double feelsLike,
        Integer humidity,
        Double precipitation,
        Double windSpeed,
        Double windGust,
        Double pressure,
        String condition,
        String icon,
        String updatedAt
) {
}
