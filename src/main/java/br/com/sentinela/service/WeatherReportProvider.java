package br.com.sentinela.service;

import br.com.sentinela.dto.response.CurrentWeatherDto;
import br.com.sentinela.dto.response.ForecastWeatherDto;
import br.com.sentinela.dto.response.WeatherTimeZoneDto;
import br.com.sentinela.entity.Property;
import br.com.sentinela.report.PreparedReport;
import br.com.sentinela.report.ReportProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class WeatherReportProvider implements ReportProvider {
    private static final DateTimeFormatter GENERATED_AT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private final Kind kind;
    private final PropertyService properties;
    private final WeatherService weather;

    WeatherReportProvider(Kind kind, PropertyService properties, WeatherService weather) {
        this.kind = kind;
        this.properties = properties;
        this.weather = weather;
    }

    @Override public String code() { return kind.code; }
    @Override public String title() { return kind.title; }
    @Override public String templatePath() { return "reports/weather/weather_property_summary.jrxml"; }

    @Override
    public PreparedReport prepare(UUID userId, Map<String, Object> requested) {
        Set<String> allowed = kind == Kind.FORECAST ? Set.of("propertyId", "days") : Set.of("propertyId");
        if (!allowed.containsAll(requested.keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O relatório recebeu parâmetros não permitidos");
        }
        UUID propertyId;
        try {
            propertyId = UUID.fromString(String.valueOf(requested.get("propertyId")));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma propriedade válida");
        }
        Property property = properties.requireView(propertyId, userId);
        int days = 3;
        if (kind == Kind.FORECAST && requested.containsKey("days")) {
            Object value = requested.get("days");
            if (!(value instanceof Number number) || number.doubleValue() % 1 != 0
                    || number.intValue() < 1 || number.intValue() > 3) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe entre 1 e 3 dias de previsão");
            }
            days = number.intValue();
        }

        List<Map<String, ?>> rows = switch (kind) {
            case CURRENT -> current(property);
            case FORECAST -> forecast(property, days);
            case TIME_ZONE -> timeZone(property);
        };
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("REPORT_TITLE", title());
        parameters.put("PROPERTY_LABEL", property.getName() + " · " + property.getCity() + "/" + property.getState());
        parameters.put("GENERATED_AT", GENERATED_AT.format(LocalDateTime.now()));
        for (int i = 0; i < kind.headers.length; i++) parameters.put("HEADER" + (i + 1), kind.headers[i]);
        return new PreparedReport(parameters, rows);
    }

    private List<Map<String, ?>> current(Property property) {
        CurrentWeatherDto value = weather.searchCurrentWeather(property.getLatitude(), property.getLongitude());
        return List.of(row(value.condition(), decimal(value.temperature(), " °C"),
                decimal(value.feelsLike(), " °C"), value.humidity() + "%",
                decimal(value.precipitation(), " mm"), decimal(value.windSpeed(), " km/h")));
    }

    private List<Map<String, ?>> forecast(Property property, int days) {
        ForecastWeatherDto value = weather.searchForecast(property.getLatitude(), property.getLongitude(), days);
        JsonNode forecastDays = value.forecast().path("forecastday");
        List<Map<String, ?>> rows = new ArrayList<>();
        for (int i = 0; i < forecastDays.size(); i++) {
            JsonNode forecastDay = forecastDays.get(i);
            JsonNode day = forecastDay.path("day");
            rows.add(row(forecastDay.path("date").asText(), decimal(day.path("mintemp_c").asDouble(), " °C"),
                    decimal(day.path("maxtemp_c").asDouble(), " °C"),
                    decimal(day.path("totalprecip_mm").asDouble(), " mm"),
                    day.path("avghumidity").asInt() + "%", day.path("condition").path("text").asText()));
        }
        return rows;
    }

    private List<Map<String, ?>> timeZone(Property property) {
        WeatherTimeZoneDto value = weather.searchTimeZone(property.getLatitude(), property.getLongitude());
        return List.of(row(value.city(), value.region(), value.country(), value.timeZone(),
                value.localTime(), decimal(value.latitude(), "") + ", " + decimal(value.longitude(), "")));
    }

    private String decimal(Number value, String unit) {
        return value == null ? "—" : String.format(PT_BR, "%.1f", value.doubleValue()) + unit;
    }

    private Map<String, Object> row(String... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) row.put("c" + (i + 1), values[i] == null ? "" : values[i]);
        return row;
    }

    enum Kind {
        CURRENT("weather_current_by_property", "WeatherAPI · Condições Atuais Por Propriedade",
                new String[]{"Condição", "Temperatura", "Sensação", "Umidade", "Precipitação", "Vento"}),
        FORECAST("weather_forecast_by_property", "WeatherAPI · Previsão Diária Por Propriedade",
                new String[]{"Data", "Mínima", "Máxima", "Chuva Prevista", "Umidade", "Condição"}),
        TIME_ZONE("weather_time_zone_by_property", "WeatherAPI · Fuso Horário Por Propriedade",
                new String[]{"Localidade", "Região", "País", "Fuso Horário", "Hora Local", "Coordenadas"});

        final String code;
        final String title;
        final String[] headers;
        Kind(String code, String title, String[] headers) {
            this.code = code;
            this.title = title;
            this.headers = headers;
        }
    }
}

@Configuration
class WeatherReportConfiguration {
    @Bean ReportProvider weatherCurrentReport(PropertyService properties, WeatherService weather) {
        return new WeatherReportProvider(WeatherReportProvider.Kind.CURRENT, properties, weather);
    }
    @Bean ReportProvider weatherForecastReport(PropertyService properties, WeatherService weather) {
        return new WeatherReportProvider(WeatherReportProvider.Kind.FORECAST, properties, weather);
    }
    @Bean ReportProvider weatherTimeZoneReport(PropertyService properties, WeatherService weather) {
        return new WeatherReportProvider(WeatherReportProvider.Kind.TIME_ZONE, properties, weather);
    }
}
