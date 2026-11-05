package br.com.sentinela.controller;

import br.com.sentinela.enums.Feature;
import br.com.sentinela.infra.security.RequiresFeature;
import br.com.sentinela.dto.response.CurrentWeatherDto;
import br.com.sentinela.dto.response.ForecastWeatherDto;
import br.com.sentinela.dto.response.WeatherTimeZoneDto;
import br.com.sentinela.service.WeatherService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
@RequiresFeature(Feature.BASE)
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/current")
    public ResponseEntity<CurrentWeatherDto> getCurrentWeather(
            @RequestParam Double latitude,
            @RequestParam Double longitude) {

        if (latitude == null || longitude == null
                || !Double.isFinite(latitude)
                || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180) {
            return ResponseEntity.badRequest().build();
        }

        CurrentWeatherDto weather = weatherService.searchCurrentWeather(
                latitude, longitude);

        return ResponseEntity.ok(weather);
    }

    @GetMapping("/forecast")
    public ResponseEntity<ForecastWeatherDto> getForecast(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(defaultValue = "3") Integer days) {

        if (!isValidCoordinates(latitude, longitude) || days < 1 || days > 14) {
            return ResponseEntity.badRequest().build();
        }

        ForecastWeatherDto forecast = weatherService.searchForecast(
                latitude, longitude, days);

        return ResponseEntity.ok(forecast);
    }

    @GetMapping("/time-zone")
    public ResponseEntity<WeatherTimeZoneDto> getTimeZone(
            @RequestParam Double latitude,
            @RequestParam Double longitude) {

        if (!isValidCoordinates(latitude, longitude)) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(weatherService.searchTimeZone(latitude, longitude));
    }

    private boolean isValidCoordinates(Double latitude, Double longitude) {
        return latitude != null && longitude != null
                && Double.isFinite(latitude)
                && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180;
    }
}
