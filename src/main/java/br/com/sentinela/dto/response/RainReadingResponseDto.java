package br.com.sentinela.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record RainReadingResponseDto(
        @JsonProperty("codigo") long stationCode,
        @JsonProperty("nome") String stationName,
        @JsonProperty("municipio") String city,
        @JsonProperty("uf") String state,
        @JsonProperty("bacia") String watershed,
        @JsonProperty("responsavel") String responsible,
        @JsonProperty("chuvaMm24h") double rainfallMm24h,
        @JsonProperty("classe") String readingClass,
        @JsonProperty("dataUltimoDado") OffsetDateTime lastReadingAt,
        @JsonProperty("idadeHoras") double ageHours,
        Double latitude,
        Double longitude,
        boolean requiresAttention
) {
}
