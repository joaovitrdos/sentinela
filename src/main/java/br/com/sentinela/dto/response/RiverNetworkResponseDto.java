package br.com.sentinela.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RiverNetworkResponseDto(
        String type,
        List<RiverFeatureDto> features
) {

    public static RiverNetworkResponseDto of(List<RiverFeatureDto> features) {
        return new RiverNetworkResponseDto("FeatureCollection", features);
    }

    public record RiverFeatureDto(
            String type,
            long id,
            RiverPropertiesDto properties,
            RiverGeometryDto geometry
    ) {

        public static RiverFeatureDto of(long id, String name, int lengthKm, double[][][] coordinates) {
            return new RiverFeatureDto("Feature", id, new RiverPropertiesDto(name, lengthKm),
                    new RiverGeometryDto("MultiLineString", coordinates));
        }
    }

    public record RiverPropertiesDto(
            @JsonProperty("nome") String name,
            @JsonProperty("km") int lengthKm) {
    }

    public record RiverGeometryDto(String type, double[][][] coordinates) {
    }
}
