package br.com.sentinela.dto.response;

public record WeatherTimeZoneDto(
        String city,
        String region,
        String country,
        Double latitude,
        Double longitude,
        String timeZone,
        Long localTimeEpoch,
        String localTime
) {
}
