package br.com.sentinela.service;

import br.com.sentinela.dto.response.HydroStationResponseDto;
import br.com.sentinela.infra.client.snirh.SnirhLayerClient;
import br.com.sentinela.dto.response.ArcGisLayerResponseDto.LayerFeatureDto;
import br.com.sentinela.infra.config.SnirhHydroProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StationServiceTest {

    private SnirhLayerClient client;
    private StationService service;

    @BeforeEach
    void setUp() {
        client = mock(SnirhLayerClient.class);
        SnirhHydroProperties properties = new SnirhHydroProperties("niveis", "estacoes", "rios", 1000, 6, 4, 10L, 0.01,
                Duration.ofMinutes(10), Duration.ofHours(12), Duration.ofDays(7), false);
        service = new StationService(client, properties);
    }

    @Test
    void converteEstacaoDoInventario() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("Codigo", 47001);
        attributes.put("Nome", "MARACANÃ");
        attributes.put("TipoEstacao", "Fluviométrica");
        attributes.put("Operando", "Sim");
        attributes.put("EstacaoTelemetrica", "Não");
        attributes.put("Rio", " ");
        attributes.put("UF", "PARÁ");
        attributes.put("ResponsavelSigla", "ANA");
        attributes.put("Latitude", -0.83);
        attributes.put("Longitude", -47.5);

        HydroStationResponseDto station = service.toStation(new LayerFeatureDto(attributes, null));

        assertThat(station.type()).isEqualTo("FLUVIOMETRICA");
        assertThat(station.operating()).isTrue();
        assertThat(station.telemetric()).isFalse();
        assertThat(station.river()).isNull();
        assertThat(station.responsible()).isEqualTo("ANA");
        assertThat(station.latitude()).isEqualTo(-0.83);
        assertThat(station.longitude()).isEqualTo(-47.5);
    }

    @Test
    void ignoraEstacaoSemPosicao() {
        assertThat(service.toStation(new LayerFeatureDto(Map.of("Codigo", 1, "Latitude", -1.0), null))).isNull();
    }

    @Test
    void inventarioFicaEmCacheEntreChamadas() {
        Map<String, Object> attributes = Map.of("Codigo", 1, "Latitude", -1.0, "Longitude", -50.0,
                "TipoEstacao", "Pluviométrica");
        when(client.fetchAll(eq("estacoes"), anyString(), anyString(), anyBoolean()))
                .thenReturn(List.of(new LayerFeatureDto(attributes, null)));

        assertThat(service.getStations()).hasSize(1);
        assertThat(service.getStations()).hasSize(1);

        verify(client, times(1)).fetchAll(eq("estacoes"), anyString(), anyString(), anyBoolean());
    }
}
