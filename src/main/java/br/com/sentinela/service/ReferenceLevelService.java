package br.com.sentinela.service;

import br.com.sentinela.dto.response.RiverLevelResponseDto;
import br.com.sentinela.infra.cache.SingleValueCache;
import br.com.sentinela.infra.client.snirh.SnirhLayerClient;
import br.com.sentinela.infra.client.snirh.SnirhText;
import br.com.sentinela.dto.response.ArcGisLayerResponseDto.LayerFeatureDto;
import br.com.sentinela.dto.response.ArcGisLayerResponseDto.LayerGeometryDto;
import br.com.sentinela.infra.config.SnirhHydroProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ReferenceLevelService {

    private static final String FIELDS =
            "Codigo,Nome,Bacia,SubBacia,Municipio,Estado,Responsavel,Operadora,Projeto,"
                    + "Status_Estacao,Data_ult_dado,Ult_Dado,Status_Dado";

    private final SnirhLayerClient client;
    private final SnirhHydroProperties properties;
    private final ZoneId timeZone;
    private final SingleValueCache<List<RiverLevelResponseDto>> cache;

    public ReferenceLevelService(SnirhLayerClient client, SnirhHydroProperties properties,
                                 @Qualifier("sentinelaTimeZone") ZoneId timeZone) {
        this.client = client;
        this.properties = properties;
        this.timeZone = timeZone;
        this.cache = new SingleValueCache<>(properties.levelsCache(), this::load);
    }

    public List<RiverLevelResponseDto> getRiverLevels() {
        return cache.get();
    }

    private List<RiverLevelResponseDto> load() {
        Instant now = Instant.now();
        List<RiverLevelResponseDto> levels = client.fetchAll(properties.levelsUrl(), FIELDS, "estcodigo", true).stream()
                .map(feature -> toRiverLevel(feature, now))
                .filter(Objects::nonNull)
                .toList();
        return latestPerStation(levels, now);
    }

    RiverLevelResponseDto toRiverLevel(LayerFeatureDto feature, Instant now) {
        Long stationCode = feature.integer("Codigo");
        LayerGeometryDto geometry = feature.geometry();
        if (stationCode == null || geometry == null || geometry.latitude() == null || geometry.longitude() == null) {
            return null;
        }

        Long epochMillis = feature.integer("Data_ult_dado");
        Instant lastReadingAt = epochMillis != null ? Instant.ofEpochMilli(epochMillis) : null;
        String description = feature.text("Status_Dado");
        String status = levelStatus(description);

        return new RiverLevelResponseDto(
                stationCode,
                feature.text("Nome"),
                feature.text("Municipio"),
                feature.text("Estado"),
                feature.text("Bacia"),
                feature.text("SubBacia"),
                feature.text("Responsavel"),
                feature.text("Operadora"),
                feature.text("Projeto"),
                stationStatus(feature.text("Status_Estacao")),
                feature.number("Ult_Dado"),
                status,
                description,
                lastReadingAt != null ? lastReadingAt.atZone(timeZone).toOffsetDateTime() : null,
                lastReadingAt != null ? ageHours(lastReadingAt, now) : null,
                geometry.latitude(),
                geometry.longitude(),
                "ATENCAO".equals(status));
    }

    static List<RiverLevelResponseDto> latestPerStation(List<RiverLevelResponseDto> levels, Instant now) {
        Comparator<RiverLevelResponseDto> byDate = Comparator.comparing(
                (RiverLevelResponseDto level) -> level.lastReadingAt() == null ? null : level.lastReadingAt().toInstant(),
                Comparator.nullsFirst(Comparator.naturalOrder()));

        Map<Long, RiverLevelResponseDto> latest = new LinkedHashMap<>();
        for (RiverLevelResponseDto level : levels) {
            if (level.lastReadingAt() != null && level.lastReadingAt().toInstant().isAfter(now)) {
                continue;
            }
            latest.merge(level.stationCode(), level, (current, candidate) ->
                    byDate.compare(candidate, current) > 0 ? candidate : current);
        }
        return List.copyOf(latest.values());
    }

    static String levelStatus(String description) {
        String normalizedDescription = SnirhText.normalize(description);
        if (normalizedDescription.contains("referencia")) {
            return "SEM_REFERENCIA";
        }
        if (normalizedDescription.startsWith("sem dados")) {
            return "SEM_DADOS";
        }
        if (normalizedDescription.contains("normal")) {
            return "NORMAL";
        }
        if (normalizedDescription.contains("atencao") || normalizedDescription.startsWith("acima")) {
            return "ATENCAO";
        }
        if (normalizedDescription.contains("estiagem") || normalizedDescription.startsWith("abaixo")) {
            return "ESTIAGEM";
        }
        return "DESCONHECIDO";
    }

    static String stationStatus(String situation) {
        String normalizedSituation = SnirhText.normalize(situation);
        if (normalizedSituation.startsWith("ativ")) {
            return "ATIVA";
        }
        if (normalizedSituation.startsWith("manutencao")) {
            return "MANUTENCAO";
        }
        return null;
    }

    static double ageHours(Instant lastData, Instant now) {
        double hours = Duration.between(lastData, now).toMinutes() / 60.0;
        return Math.round(hours * 10) / 10.0;
    }
}
