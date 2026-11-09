package br.com.sentinela.service;

import br.com.sentinela.util.Util;

import br.com.sentinela.dto.response.RainReadingResponseDto;
import br.com.sentinela.infra.cache.SingleValueCache;
import br.com.sentinela.infra.client.snirh.SnirhRainClient;
import br.com.sentinela.infra.client.snirh.SnirhText;
import br.com.sentinela.dto.response.ArcGisQueryResponseDto.AttributesDto;
import br.com.sentinela.dto.response.ArcGisQueryResponseDto.FeatureDto;
import br.com.sentinela.dto.response.ArcGisQueryResponseDto.GeometryDto;
import br.com.sentinela.infra.config.SnirhProperties;
import br.com.sentinela.exception.SnirhUnavailableException;
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
        Instant now = Instant.now();
        return cache.get().stream().map(reading -> new RainReadingResponseDto(
                reading.stationCode(), reading.stationName(), reading.city(), reading.state(), reading.watershed(),
                reading.responsible(), reading.rainfallMm24h(), reading.readingClass(), reading.lastReadingAt(),
                ReferenceLevelService.ageHours(reading.lastReadingAt().toInstant(), now), reading.latitude(),
                reading.longitude(), reading.requiresAttention())).toList();
    }

    private List<RainReadingResponseDto> load() {
        Instant now = Instant.now();
        List<FeatureDto> features = client.fetchAllFeatures();
        if (Util.isNullOrEmpty(features)) {
            throw new SnirhUnavailableException("SNIRH returned no rainfall stations");
        }
        List<RainReadingResponseDto> readings = features.stream()
                .map(feature -> toReading(feature, now))
                .filter(Util::nonNull)
                .toList();
        if (Util.isNullOrEmpty(readings)) {
            throw new SnirhUnavailableException("SNIRH returned no valid rainfall readings");
        }
        return readings;
    }

    RainReadingResponseDto toReading(FeatureDto feature, Instant now) {
        AttributesDto attributes = feature.attributes();
        if (Util.isNull(attributes) || Util.isNull(attributes.stationCode()) || Util.isNull(attributes.rainfallMm24h())
                || Util.isNull(attributes.lastReadingAt())) {
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
                Util.nonNull(geometry) ? geometry.latitude() : null,
                Util.nonNull(geometry) ? geometry.longitude() : null,
                requiresAttention);
    }
}
