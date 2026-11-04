package br.com.sentinela.service;

import br.com.sentinela.dto.response.RainReadingResponseDto;
import br.com.sentinela.infra.client.snirh.SnirhRainClient;
import br.com.sentinela.dto.response.ArcGisQueryResponseDto.AttributesDto;
import br.com.sentinela.dto.response.ArcGisQueryResponseDto.FeatureDto;
import br.com.sentinela.dto.response.ArcGisQueryResponseDto.GeometryDto;
import br.com.sentinela.infra.config.SnirhProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RainServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");

    private SnirhRainClient client;
    private RainService service;

    @BeforeEach
    void setUp() {
        client = mock(SnirhRainClient.class);
        service = new RainService(client, new SnirhProperties("chuva", 1000, 4, 10L, 300, Duration.ofMinutes(10)), ZoneOffset.UTC);
    }

    @Test
    void converteLeituraDeChuvaComIdadeDoDado() {
        RainReadingResponseDto reading = service.toReading(feature(12.5, "Chuva Moderada", "2026-10-22T10:30:00Z"), NOW);

        assertThat(reading.stationCode()).isEqualTo(10L);
        assertThat(reading.rainfallMm24h()).isEqualTo(12.5);
        assertThat(reading.readingClass()).isEqualTo("Chuva Moderada");
        assertThat(reading.ageHours()).isEqualTo(1.5);
        assertThat(reading.requiresAttention()).isFalse();
        assertThat(reading.latitude()).isEqualTo(-1.4);
        assertThat(reading.longitude()).isEqualTo(-48.5);
    }

    @Test
    void marcaAtencaoSomenteNasClassesDeChuvaForteEMuitoForte() {
        for (String readingClass : List.of("Chuva Forte", "Chuva Muito Forte", " CHUVA FORTE ")) {
            assertThat(service.toReading(feature(50.0, readingClass, "2026-10-22T10:00:00Z"), NOW).requiresAttention())
                    .as(readingClass).isTrue();
        }
        for (String readingClass : List.of("Chuva Moderada", "Chuva Fraca", "Sem Chuva", "Desconhecida")) {
            assertThat(service.toReading(feature(5.0, readingClass, "2026-10-22T10:00:00Z"), NOW).requiresAttention())
                    .as(readingClass).isFalse();
        }
    }

    @Test
    void descartaLeituraSemDadosImplausivelOuNoFuturo() {
        assertThat(service.toReading(feature(0.0, "Sem Dados", "2026-10-22T10:00:00Z"), NOW)).isNull();
        assertThat(service.toReading(feature(null, "Sem Chuva", "2026-10-22T10:00:00Z"), NOW)).isNull();
        assertThat(service.toReading(feature(-1.0, "Sem Chuva", "2026-10-22T10:00:00Z"), NOW)).isNull();
        assertThat(service.toReading(feature(450.0, "Chuva Muito Forte", "2026-10-22T10:00:00Z"), NOW)).isNull();
        assertThat(service.toReading(feature(5.0, "Chuva Fraca", "2027-01-06T20:00:00Z"), NOW)).isNull();
        assertThat(service.toReading(feature(300.0, "Chuva Muito Forte", "2026-10-22T10:00:00Z"), NOW)).isNotNull();
    }

    @Test
    void chuvaFicaEmCacheEntreChamadas() {
        when(client.fetchAllFeatures()).thenReturn(List.of(feature(1.0, "Chuva Fraca", Instant.now().toString())));

        assertThat(service.getRain24h()).hasSize(1);
        assertThat(service.getRain24h()).hasSize(1);

        verify(client, times(1)).fetchAllFeatures();
    }

    private static FeatureDto feature(Double rain, String classe, String date) {
        AttributesDto attributes = new AttributesDto(10L, "EST", "BACIA", "BELÉM", "PARÁ", "ANA", "Ativo",
                Instant.parse(date).toEpochMilli(), rain, classe);
        return new FeatureDto(attributes, new GeometryDto(-48.5, -1.4));
    }
}
