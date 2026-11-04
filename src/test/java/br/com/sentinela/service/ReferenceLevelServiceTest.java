package br.com.sentinela.service;

import br.com.sentinela.dto.response.RiverLevelResponseDto;
import br.com.sentinela.infra.client.snirh.SnirhLayerClient;
import br.com.sentinela.dto.response.ArcGisLayerResponseDto.LayerFeatureDto;
import br.com.sentinela.dto.response.ArcGisLayerResponseDto.LayerGeometryDto;
import br.com.sentinela.infra.config.SnirhHydroProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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

class ReferenceLevelServiceTest {

    private SnirhLayerClient client;
    private ReferenceLevelService service;

    @BeforeEach
    void setUp() {
        client = mock(SnirhLayerClient.class);
        SnirhHydroProperties properties = new SnirhHydroProperties("niveis", "estacoes", "rios", 1000, 6, 4, 10L, 0.01,
                Duration.ofMinutes(10), Duration.ofHours(12), Duration.ofDays(7), false);
        service = new ReferenceLevelService(client, properties, ZoneOffset.UTC);
    }

    @Test
    void classificaOTextoLongoDoSnirhEmCodigoCurto() {
        assertThat(ReferenceLevelService.levelStatus(
                "Cota de Atenção (SGB/CPRM) ou Acima da Permanência Anual de 10%")).isEqualTo("ATENCAO");
        assertThat(ReferenceLevelService.levelStatus(
                "Cota Normal - Entre Cotas de Atenção (SGB/CPRM)")).isEqualTo("NORMAL");
        assertThat(ReferenceLevelService.levelStatus(
                "Cota de Estiagem - Abaixo da  Permanência Anual de 90%")).isEqualTo("ESTIAGEM");
        assertThat(ReferenceLevelService.levelStatus("Sem Dados Atualizados")).isEqualTo("SEM_DADOS");
        assertThat(ReferenceLevelService.levelStatus("Sem Dados de Referência")).isEqualTo("SEM_REFERENCIA");
        assertThat(ReferenceLevelService.levelStatus(null)).isEqualTo("DESCONHECIDO");
    }

    @Test
    void classificaOsCodigosCurtosQueOCampoRealmenteTraz() {
        assertThat(ReferenceLevelService.levelStatus("Normal")).isEqualTo("NORMAL");
        assertThat(ReferenceLevelService.levelStatus("Acima_10%_permanencia")).isEqualTo("ATENCAO");
        assertThat(ReferenceLevelService.levelStatus("Abaixo_90%_permanencia")).isEqualTo("ESTIAGEM");
        assertThat(ReferenceLevelService.levelStatus("Sem dados atualizados")).isEqualTo("SEM_DADOS");
        assertThat(ReferenceLevelService.levelStatus("Sem dados de referencia")).isEqualTo("SEM_REFERENCIA");
    }

    @Test
    void classificaASituacaoDaEstacao() {
        assertThat(ReferenceLevelService.stationStatus("Ativo")).isEqualTo("ATIVA");
        assertThat(ReferenceLevelService.stationStatus("Manutenção")).isEqualTo("MANUTENCAO");
        assertThat(ReferenceLevelService.stationStatus("Extinta")).isNull();
        assertThat(ReferenceLevelService.stationStatus(null)).isNull();
    }

    @Test
    void converteLeituraDeNivelComIdadeDoDado() {
        Instant now = Instant.parse("2026-10-22T12:00:00Z");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("Codigo", 12345);
        attributes.put("Nome", "RIO X");
        attributes.put("Estado", "PARÁ");
        attributes.put("Operadora", "SGB-CPRM");
        attributes.put("Projeto", "RHN");
        attributes.put("Status_Estacao", "Manutenção");
        attributes.put("Ult_Dado", 315.0);
        attributes.put("Status_Dado", "Cota Normal - Entre Cotas");
        attributes.put("Data_ult_dado", Instant.parse("2026-10-22T09:00:00Z").toEpochMilli());

        RiverLevelResponseDto level = service.toRiverLevel(
                new LayerFeatureDto(attributes, LayerGeometryDto.point(-50.0, -3.0)), now);

        assertThat(level.stationCode()).isEqualTo(12345L);
        assertThat(level.status()).isEqualTo("NORMAL");
        assertThat(level.operator()).isEqualTo("SGB-CPRM");
        assertThat(level.project()).isEqualTo("RHN");
        assertThat(level.stationSituation()).isEqualTo("MANUTENCAO");
        assertThat(level.levelCm()).isEqualTo(315.0);
        assertThat(level.ageHours()).isEqualTo(3.0);
        assertThat(level.requiresAttention()).isFalse();
        assertThat(level.latitude()).isEqualTo(-3.0);
        assertThat(level.longitude()).isEqualTo(-50.0);
    }

    @Test
    void marcaAtencaoSomenteNoStatusDeAtencaoDoSnirh() {
        for (String description : List.of("Cota de Atenção (SGB/CPRM)", "Acima_10%_permanencia")) {
            RiverLevelResponseDto level = service.toRiverLevel(new LayerFeatureDto(
                    Map.of("Codigo", 1, "Status_Dado", description), LayerGeometryDto.point(-50.0, -3.0)), Instant.now());
            assertThat(level.requiresAttention()).as(description).isTrue();
        }
        for (String description : List.of("Normal", "Abaixo_90%_permanencia", "Sem dados atualizados",
                "Sem dados de referencia", "Desconhecido")) {
            RiverLevelResponseDto level = service.toRiverLevel(new LayerFeatureDto(
                    Map.of("Codigo", 1, "Status_Dado", description), LayerGeometryDto.point(-50.0, -3.0)), Instant.now());
            assertThat(level.requiresAttention()).as(description).isFalse();
        }
    }

    @Test
    void mantemSoALeituraMaisRecenteDeCadaEstacaoIgnorandoDataNoFuturo() {
        Instant now = Instant.parse("2026-10-22T12:00:00Z");
        RiverLevelResponseDto older = level(1L, "2026-10-22T03:00:00Z");
        RiverLevelResponseDto newer = level(1L, "2026-10-22T11:00:00Z");
        RiverLevelResponseDto future = level(1L, "2027-05-01T00:00:00Z");
        RiverLevelResponseDto other = level(2L, "2026-10-21T00:00:00Z");

        List<RiverLevelResponseDto> latest = ReferenceLevelService.latestPerStation(List.of(older, newer, future, other), now);

        assertThat(latest).hasSize(2);
        assertThat(latest).contains(newer, other);
    }

    @Test
    void ignoraNivelSemCodigoOuSemPosicao() {
        Map<String, Object> withoutCode = Map.of("Nome", "SEM CODIGO");
        assertThat(service.toRiverLevel(new LayerFeatureDto(withoutCode, LayerGeometryDto.point(-50.0, -3.0)),
                Instant.now())).isNull();
        assertThat(service.toRiverLevel(new LayerFeatureDto(Map.of("Codigo", 1), null), Instant.now())).isNull();
    }

    @Test
    void niveisFicamEmCacheEntreChamadas() {
        Map<String, Object> attributes = Map.of("Codigo", 1, "Status_Dado", "Normal");
        when(client.fetchAll(eq("niveis"), anyString(), anyString(), anyBoolean()))
                .thenReturn(List.of(new LayerFeatureDto(attributes, LayerGeometryDto.point(-50.0, -3.0))));

        assertThat(service.getRiverLevels()).hasSize(1);
        assertThat(service.getRiverLevels()).hasSize(1);

        verify(client, times(1)).fetchAll(eq("niveis"), anyString(), anyString(), anyBoolean());
    }

    private static RiverLevelResponseDto level(long codigo, String date) {
        return new RiverLevelResponseDto(codigo, "EST " + codigo, null, null, null, null, null, null, null, "ATIVA",
                100.0, "NORMAL", "Normal", Instant.parse(date).atOffset(ZoneOffset.UTC), 0.0, -3.0, -50.0, false);
    }
}
