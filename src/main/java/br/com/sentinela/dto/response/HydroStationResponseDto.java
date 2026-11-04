package br.com.sentinela.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HydroStationResponseDto(
        @JsonProperty("codigo") long code,
        @JsonProperty("nome") String name,
        @JsonProperty("tipo") String type,
        @JsonProperty("operando") boolean operating,
        @JsonProperty("telemetrica") boolean telemetric,
        @JsonProperty("rio") String river,
        @JsonProperty("uf") String state,
        @JsonProperty("municipio") String city,
        @JsonProperty("bacia") String watershed,
        @JsonProperty("subBacia") String subWatershed,
        @JsonProperty("responsavel") String responsible,
        @JsonProperty("operadora") String operator,
        Double altitude,
        @JsonProperty("areaDrenagem") Double drainageArea,
        double latitude,
        double longitude
) {
}
