package br.com.sentinela.service;

import br.com.sentinela.util.Util;

import br.com.sentinela.dto.response.SprayWindowResponseDto;
import br.com.sentinela.dto.response.SprayWindowResponseDto.Hour;
import br.com.sentinela.dto.response.SprayWindowResponseDto.Window;
import br.com.sentinela.util.WeatherAttribution;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class SprayWindowService {
    private static final double MIN_WIND_KPH = 3;
    private static final double MAX_WIND_KPH = 10;
    private static final double MAX_TEMPERATURE_C = 30;
    private static final int MIN_HUMIDITY = 55;
    private static final int MAX_RAIN_CHANCE = 40;
    private static final String DISCLAIMER = WeatherAttribution.DISCLAIMER
            + " Confira as condições no campo e siga a bula do produto.";

    private final PropertyService properties;
    private final PropertyForecastService forecasts;

    public SprayWindowResponseDto windows(UUID userId, UUID propertyId) {
        var forecast = forecasts.forecast(properties.requireView(propertyId, userId));
        List<Hour> hours = hours(forecast.forecast(), forecast.location().path("localtime_epoch").asLong());
        return new SprayWindowResponseDto(hours, windows(hours), WeatherAttribution.SOURCE, DISCLAIMER);
    }

    static List<Hour> hours(JsonNode forecast, long nowEpoch) {
        List<Hour> hours = new ArrayList<>();
        for (JsonNode day : forecast.path("forecastday")) {
            for (JsonNode hour : day.path("hour")) {
                if (hour.path("time_epoch").asLong() + 3_600 <= nowEpoch) continue;
                hours.add(hour(hour));
            }
        }
        return hours;
    }

    private static Hour hour(JsonNode hour) {
        double temperature = hour.path("temp_c").asDouble();
        int humidity = hour.path("humidity").asInt();
        double wind = hour.path("wind_kph").asDouble();
        int rainChance = hour.path("chance_of_rain").asInt();
        double precipitation = hour.path("precip_mm").asDouble();
        List<String> reasons = new ArrayList<>();
        if (wind < MIN_WIND_KPH) reasons.add("Vento abaixo de 3 km/h: risco de inversão térmica");
        if (wind > MAX_WIND_KPH) reasons.add("Vento acima de 10 km/h: risco de deriva");
        if (temperature > MAX_TEMPERATURE_C) reasons.add("Temperatura acima de 30 °C");
        if (humidity < MIN_HUMIDITY) reasons.add("Umidade abaixo de 55%");
        if (rainChance >= MAX_RAIN_CHANCE || precipitation > 0) reasons.add("Risco de chuva");
        return new Hour(hour.path("time").asText(), Util.isNullOrEmpty(reasons), temperature, humidity, wind, rainChance,
                precipitation, reasons);
    }

    static List<Window> windows(List<Hour> hours) {
        List<Window> windows = new ArrayList<>();
        Hour first = null;
        Hour last = null;
        int count = 0;
        for (Hour hour : hours) {
            if (hour.suitable()) {
                if (Util.isNull(first)) first = hour;
                last = hour;
                count++;
            } else if (Util.nonNull(first)) {
                windows.add(new Window(first.time(), last.time(), count));
                first = null;
                count = 0;
            }
        }
        if (Util.nonNull(first)) windows.add(new Window(first.time(), last.time(), count));
        return windows;
    }
}
