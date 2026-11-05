package br.com.sentinela.service;

import br.com.sentinela.dto.response.ForecastWeatherDto;
import br.com.sentinela.entity.Property;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/** Previsão de 3 dias, limite do plano gratuito da WeatherAPI; o cache poupa a cota mensal de chamadas. */
@Service @RequiredArgsConstructor
public class PropertyForecastService {
    private static final int FREE_PLAN_DAYS = 3;

    private final WeatherService weather;
    private final Cache<String, ForecastWeatherDto> cache = Caffeine.newBuilder()
            .maximumSize(2_000).expireAfterWrite(Duration.ofMinutes(30)).build();

    public ForecastWeatherDto forecast(Property property) {
        String key = String.format(Locale.ROOT, "%.2f,%.2f", property.getLatitude(), property.getLongitude());
        return cache.get(key, ignored -> weather.searchForecast(property.getLatitude(), property.getLongitude(), FREE_PLAN_DAYS));
    }
}
