package br.com.sentinela.dto.response;

import br.com.sentinela.util.WeatherAttribution;

public record WeatherTimeZoneDto(
        String city,
        String region,
        String country,
        Double latitude,
        Double longitude,
        String timeZone,
        Long localTimeEpoch,
        String localTime,
        String source
) {
    public WeatherTimeZoneDto(String city, String region, String country, Double latitude, Double longitude,
                              String timeZone, Long localTimeEpoch, String localTime) {
        this(city, region, country, latitude, longitude, timeZone, localTimeEpoch, localTime, WeatherAttribution.SOURCE);
    }
}
