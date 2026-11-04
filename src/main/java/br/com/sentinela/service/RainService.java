package br.com.sentinela.service;

import br.com.sentinela.dto.response.RainReadingResponseDto;
import br.com.sentinela.infra.cache.SingleValueCache;
import br.com.sentinela.infra.client.snirh.SnirhRainClient;
import br.com.sentinela.infra.client.snirh.SnirhText;
import br.com.sentinela.dto.response.ArcGisQueryResponseDto.AttributesDto;
import br.com.sentinela.dto.response.ArcGisQueryResponseDto.FeatureDto;
import br.com.sentinela.dto.response.ArcGisQueryResponseDto.GeometryDto;
import br.com.sentinela.infra.config.SnirhProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@Service
public class RainService {

    private final SnirhRainClient client;
    private final SnirhProperties properties;
    private final ZoneId timeZone;
    private final SingleValueCache<List<RainReadingResponseDto>> cache;

    public RainService(SnirhRainClient client, SnirhProperties properties,
                       @Qualifier("sentinelaTimeZone") ZoneId timeZone) {
        this.client = client;
        this.properties = properties;
        this.timeZone = timeZone;
        this.cache = new SingleValueCache<>(properties.cache(), this::load);
    }

    public List<RainReadingResponseDto> getRain24h() {
        return cache.get();
    }

    private List<RainReadingResponseDto> load() {
        Instant now = Instant.now();
        return client.fetchAllFeatures().stream()
                .map(feature -> toReading(feature, now))
                .filter(Objects::nonNull)
                .toList();
    }

    RainReadingResponseDto toReading(FeatureDto feature, Instant now) {
        AttributesDto attributes = feature.attributes();
        if (attributes == null || attributes.stationCode() == null || attributes.rainfallMm24h() == null
                || attributes.lastReadingAt() == null) {
            return null;
        }
        double rainfall = attributes.rainfallMm24h();
        Instant lastReadingAt = Instant.ofEpochMilli(attributes.lastReadingAt());
        if (rainfall < 0 || rainfall > properties.maxRainfallMm24h() || lastReadingAt.isAfter(now)
                || SnirhText.normalize(attributes.readingStatus()).startsWith("sem dados")) {
            return null;
        }

        GeometryDto geometry = feature.geometry();
        String readingClass = SnirhText.normalize(attributes.readingStatus());
        boolean requiresAttention = "chuva forte".equals(readingClass) || "chuva muito forte".equals(readingClass);
        return new RainReadingResponseDto(
                attributes.stationCode(),
                attributes.name(),
                attributes.city(),
                attributes.state(),
                attributes.watershed(),
                attributes.responsible(),
                rainfall,
                attributes.readingStatus(),
                lastReadingAt.atZone(timeZone).toOffsetDateTime(),
                ReferenceLevelService.ageHours(lastReadingAt, now),
                geometry != null ? geometry.latitude() : null,
                geometry != null ? geometry.longitude() : null,
                requiresAttention);
    }
}
