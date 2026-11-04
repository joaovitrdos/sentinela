package br.com.sentinela.service;

import br.com.sentinela.dto.response.RiverNetworkResponseDto;
import br.com.sentinela.dto.response.RiverNetworkResponseDto.RiverFeatureDto;
import br.com.sentinela.infra.client.snirh.SnirhLayerClient;
import br.com.sentinela.dto.response.ArcGisLayerResponseDto.LayerFeatureDto;
import br.com.sentinela.dto.response.ArcGisLayerResponseDto.LayerGeometryDto;
import br.com.sentinela.infra.config.SnirhHydroProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiverServiceTest {

    private SnirhLayerClient client;
    private RiverService service;

    @BeforeEach
    void setUp() {
        client = mock(SnirhLayerClient.class);
        SnirhHydroProperties properties = new SnirhHydroProperties("niveis", "estacoes", "rios", 1000, 6, 4, 10L, 0.01,
                Duration.ofMinutes(10), Duration.ofHours(12), Duration.ofDays(7), false);
        service = new RiverService(client, properties);
    }

    @Test
    void arredondaAsCoordenadasETiraPontosRepetidos() {
        double[][] path = {
                {-50.12341, -3.00001},
                {-50.12349, -3.00004},
                {-50.2, -3.1},
                null,
                {-50.3}
        };

        double[][] rounded = RiverService.round(path);

        assertThat(rounded).hasDimensions(2, 2);
        assertThat(rounded[0]).containsExactly(-50.123, -3.0);
        assertThat(rounded[1]).containsExactly(-50.2, -3.1);
    }

    @Test
    void converteRioEmMultiLineStringDescartandoTrechosDeUmPonto() {
        LayerGeometryDto geometry = LayerGeometryDto.line(
                new double[][]{{-50.0, -3.0}, {-50.1, -3.1}},
                // Depois de arredondar vira um ponto so: o trecho e descartado.
                new double[][]{{-51.0, -4.0}, {-51.0001, -4.0001}});

        RiverFeatureDto river = RiverService.toRiver(
                new LayerFeatureDto(Map.of("OBJECTID", 7, "NORIOCOMP", "Rio Negro"), geometry));

        assertThat(river.type()).isEqualTo("Feature");
        assertThat(river.id()).isEqualTo(7L);
        assertThat(river.properties().name()).isEqualTo("Rio Negro");
        assertThat(river.geometry().type()).isEqualTo("MultiLineString");
        assertThat(river.geometry().coordinates()).hasDimensions(1, 2);
        assertThat(river.properties().lengthKm()).isEqualTo(16);
    }

    @Test
    void mediOComprimentoDoRioEmKm() {
        // 1 grau de latitude ~ 111,2 km
        assertThat(RiverService.lengthKm(new double[][][]{{{-50.0, 0.0}, {-50.0, 1.0}}})).isCloseTo(111.2, within(0.1));
        // No equador 1 grau de longitude tambem ~ 111,2 km; os trechos somam
        assertThat(RiverService.lengthKm(new double[][][]{
                {{-50.0, 0.0}, {-49.0, 0.0}},
                {{-50.0, 0.0}, {-50.0, 1.0}}})).isCloseTo(222.4, within(0.2));
    }

    @Test
    void devolveOsMeandrosCortadosPelaSimplificacaoUsandoOShapeLength() {
        // Linha simplificada reta de 1 grau; o rio original media 1,1 grau (meandros): 111,2 km * 1,1
        LayerGeometryDto geometry = LayerGeometryDto.line(new double[][]{{-50.0, 0.0}, {-50.0, 1.0}});

        RiverFeatureDto river = RiverService.toRiver(
                new LayerFeatureDto(Map.of("OBJECTID", 1, "Shape_Length", 1.1), geometry));

        assertThat(river.properties().lengthKm()).isEqualTo(122);
        // Shape_Length menor que a linha simplificada (dado inconsistente) nao encolhe o rio
        assertThat(RiverService.meanderFactor(0.5, new double[][][]{{{-50.0, 0.0}, {-50.0, 1.0}}})).isEqualTo(1.0);
        assertThat(RiverService.meanderFactor(null, new double[][][]{{{-50.0, 0.0}, {-50.0, 1.0}}})).isEqualTo(1.0);
    }

    @Test
    void ignoraRioSemGeometria() {
        assertThat(RiverService.toRiver(new LayerFeatureDto(Map.of("OBJECTID", 1), null))).isNull();
        assertThat(RiverService.toRiver(new LayerFeatureDto(Map.of("OBJECTID", 1), LayerGeometryDto.point(-50.0, -3.0))))
                .isNull();
    }

    @Test
    void pedeAoSnirhAsLinhasSimplificadasEGuardaEmCache() {
        LayerFeatureDto feature = new LayerFeatureDto(Map.of("OBJECTID", 1, "NORIOCOMP", "Rio Negro"),
                LayerGeometryDto.line(new double[][]{{-50.0, -3.0}, {-50.1, -3.1}}));
        when(client.fetchAll(eq("rios"), anyString(), anyString(), anyBoolean(), anyMap())).thenReturn(List.of(feature));

        service.getRivers();
        RiverNetworkResponseDto rivers = service.getRivers();

        assertThat(rivers.type()).isEqualTo("FeatureCollection");
        assertThat(rivers.features()).hasSize(1);
        verify(client, times(1)).fetchAll(eq("rios"), anyString(), anyString(), eq(true),
                argThat(params -> Double.valueOf(0.01).equals(params.get("maxAllowableOffset"))));
    }
}
