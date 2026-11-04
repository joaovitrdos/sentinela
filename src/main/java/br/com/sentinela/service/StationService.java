package br.com.sentinela.service;

import br.com.sentinela.dto.response.HydroStationResponseDto;
import br.com.sentinela.infra.cache.SingleValueCache;
import br.com.sentinela.infra.client.snirh.SnirhLayerClient;
import br.com.sentinela.infra.client.snirh.SnirhText;
import br.com.sentinela.dto.response.ArcGisLayerResponseDto.LayerFeatureDto;
import br.com.sentinela.infra.config.SnirhHydroProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class StationService {

    private static final String FIELDS =
            "Codigo,Nome,TipoEstacao,Operando,EstacaoTelemetrica,Rio,UF,Municipio,Bacia,SubBacia,"
                    + "ResponsavelSigla,OperadoraSigla,Altitude,AreaDrenagem,Latitude,Longitude";

    private final SnirhLayerClient client;
    private final SnirhHydroProperties properties;
    private final SingleValueCache<List<HydroStationResponseDto>> cache;

    public StationService(SnirhLayerClient client, SnirhHydroProperties properties) {
        this.client = client;
        this.properties = properties;
        this.cache = new SingleValueCache<>(properties.stationsCache(), this::load);
    }

    public List<HydroStationResponseDto> getStations() {
        return cache.get();
    }

    private List<HydroStationResponseDto> load() {
        return client.fetchAll(properties.stationsUrl(), FIELDS, "Codigo", false).stream()
                .map(this::toStation)
                .filter(Objects::nonNull)
                .toList();
    }

    HydroStationResponseDto toStation(LayerFeatureDto feature) {
        Long stationCode = feature.integer("Codigo");
        Double latitude = feature.number("Latitude");
        Double longitude = feature.number("Longitude");
        if (stationCode == null || latitude == null || longitude == null) {
            return null;
        }

        String stationType = SnirhText.normalize(feature.text("TipoEstacao")).startsWith("fluvio")
                ? "FLUVIOMETRICA"
                : "PLUVIOMETRICA";

        return new HydroStationResponseDto(
                stationCode,
                feature.text("Nome"),
                stationType,
                isYes(feature.text("Operando")),
                isYes(feature.text("EstacaoTelemetrica")),
                feature.text("Rio"),
                feature.text("UF"),
                feature.text("Municipio"),
                feature.text("Bacia"),
                feature.text("SubBacia"),
                feature.text("ResponsavelSigla"),
                feature.text("OperadoraSigla"),
                feature.number("Altitude"),
                feature.number("AreaDrenagem"),
                latitude,
                longitude);
    }

    private static boolean isYes(String value) {
        return SnirhText.normalize(value).equals("sim");
    }
}
