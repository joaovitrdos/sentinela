package br.com.sentinela.dto.response;

import br.com.sentinela.util.WeatherAttribution;

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
        String updatedAt,
        String source,
        String disclaimer
) {
    public CurrentWeatherDto(String city, String region, String country, Double latitude, Double longitude,
                             Double temperature, Double feelsLike, Integer humidity, Double precipitation,
                             Double windSpeed, Double windGust, Double pressure, String condition, String icon,
                             String updatedAt) {
        this(city, region, country, latitude, longitude, temperature, feelsLike, humidity, precipitation, windSpeed,
                windGust, pressure, condition, icon, updatedAt, WeatherAttribution.SOURCE, WeatherAttribution.DISCLAIMER);
    }
}
