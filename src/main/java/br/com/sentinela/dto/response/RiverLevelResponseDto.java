package br.com.sentinela.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public record RiverLevelResponseDto(
        @JsonProperty("codigo") long stationCode,
        @JsonProperty("nome") String stationName,
        @JsonProperty("municipio") String city,
        @JsonProperty("uf") String state,
        @JsonProperty("bacia") String watershed,
        @JsonProperty("subBacia") String subWatershed,
        @JsonProperty("responsavel") String responsible,
        @JsonProperty("operadora") String operator,
        @JsonProperty("projeto") String project,
        @JsonProperty("situacaoEstacao") String stationSituation,
        @JsonProperty("cotaCm") Double levelCm,
        String status,
        @JsonProperty("statusDescricao") String statusDescription,
        @JsonProperty("dataUltimoDado") OffsetDateTime lastReadingAt,
        @JsonProperty("idadeHoras") Double ageHours,
        Double latitude,
        Double longitude,
        boolean requiresAttention
) {
}
