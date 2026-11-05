package br.com.sentinela.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Histórico diário da NASA POWER: gratuito, sem chave, licença CC BY 4.0 e atraso de alguns dias. */
@Service
public class NasaPowerService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final double MISSING_LIMIT = -900;

    private final RestClient client;
    private final Cache<String, List<DailyClimate>> cache = Caffeine.newBuilder()
            .maximumSize(2_000).expireAfterWrite(Duration.ofHours(6)).build();

    public record DailyClimate(LocalDate date, double maxTemperatureC, double minTemperatureC, double rainMm) {}

    public NasaPowerService(@Qualifier("nasaPowerRestClient") RestClient client) {
        this.client = client;
    }

    public List<DailyClimate> daily(double latitude, double longitude, LocalDate from, LocalDate to) {
        String key = String.format(Locale.ROOT, "%.2f,%.2f,%s,%s", latitude, longitude, from, to);
        return cache.get(key, ignored -> parse(client.get()
                .uri(uri -> uri.queryParam("parameters", "T2M_MAX,T2M_MIN,PRECTOTCORR")
                        .queryParam("community", "AG")
                        .queryParam("latitude", latitude)
                        .queryParam("longitude", longitude)
                        .queryParam("start", from.format(DATE))
                        .queryParam("end", to.format(DATE))
                        .queryParam("format", "JSON")
                        .build())
                .retrieve()
                .body(JsonNode.class)));
    }

    static List<DailyClimate> parse(JsonNode response) {
        JsonNode parameters = response.path("properties").path("parameter");
        JsonNode maximum = parameters.path("T2M_MAX");
        List<DailyClimate> days = new ArrayList<>();
        for (String date : maximum.propertyNames()) {
            double max = maximum.path(date).asDouble();
            double min = parameters.path("T2M_MIN").path(date).asDouble(-999);
            double rain = parameters.path("PRECTOTCORR").path(date).asDouble(-999);
            if (max > MISSING_LIMIT && min > MISSING_LIMIT && rain > MISSING_LIMIT) {
                days.add(new DailyClimate(LocalDate.parse(date, DATE), max, min, rain));
            }
        }
        return days;
    }
}
