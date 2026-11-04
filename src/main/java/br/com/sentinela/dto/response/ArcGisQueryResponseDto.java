package br.com.sentinela.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ArcGisQueryResponseDto(
        @JsonProperty("features") List<FeatureDto> features,
        @JsonProperty("exceededTransferLimit") Boolean transferLimitExceeded,
        @JsonProperty("error") ArcGisErrorDto error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FeatureDto(
            @JsonProperty("attributes") AttributesDto attributes,
            @JsonProperty("geometry") GeometryDto geometry) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AttributesDto(
            @JsonProperty("estcodigo") Long stationCode,
            @JsonProperty("Nome") String name,
            @JsonProperty("Bacia") String watershed,
            @JsonProperty("Municipio") String city,
            @JsonProperty("Estado") String state,
            @JsonProperty("Responsavel") String responsible,
            @JsonProperty("Status_Estacao") String stationStatus,
            @JsonProperty("Data_ult_dado") Long lastReadingAt,
            @JsonProperty("Ult_Dado") Double rainfallMm24h,
            @JsonProperty("Status_Dado") String readingStatus) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeometryDto(
            @JsonProperty("x") Double longitude,
            @JsonProperty("y") Double latitude) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArcGisErrorDto(
            @JsonProperty("code") Integer code,
            @JsonProperty("message") String message) {
    }
}
