package br.com.sentinela.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ArcGisLayerResponseDto(
        @JsonProperty("features") List<LayerFeatureDto> features,
        @JsonProperty("count") Integer total,
        @JsonProperty("error") ArcGisQueryResponseDto.ArcGisErrorDto error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LayerGeometryDto(
            @JsonProperty("x") Double longitude,
            @JsonProperty("y") Double latitude,
            @JsonProperty("paths") double[][][] paths) {

        public static LayerGeometryDto point(double longitude, double latitude) {
            return new LayerGeometryDto(longitude, latitude, null);
        }

        public static LayerGeometryDto line(double[][]... paths) {
            return new LayerGeometryDto(null, null, paths);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LayerFeatureDto(
            @JsonProperty("attributes") Map<String, Object> attributes,
            @JsonProperty("geometry") LayerGeometryDto geometry) {

        public String text(String field) {
            Object value = attributes == null ? null : attributes.get(field);
            if (value == null) {
                return null;
            }
            String text = value.toString().trim();
            return text.isEmpty() ? null : text;
        }

        public Double number(String field) {
            Object value = attributes == null ? null : attributes.get(field);
            return value instanceof Number number ? number.doubleValue() : null;
        }

        public Long integer(String field) {
            Object value = attributes == null ? null : attributes.get(field);
            return value instanceof Number number ? number.longValue() : null;
        }
    }
}
