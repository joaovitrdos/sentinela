package br.com.sentinela.service;

import br.com.sentinela.dto.response.RiverNetworkResponseDto;
import br.com.sentinela.dto.response.RiverNetworkResponseDto.RiverFeatureDto;
import br.com.sentinela.infra.cache.SingleValueCache;
import br.com.sentinela.infra.client.snirh.SnirhLayerClient;
import br.com.sentinela.dto.response.ArcGisLayerResponseDto.LayerFeatureDto;
import br.com.sentinela.dto.response.ArcGisLayerResponseDto.LayerGeometryDto;
import br.com.sentinela.infra.config.SnirhHydroProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RiverService {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double PRECISION = 1000.0;

    private final SnirhLayerClient client;
    private final SnirhHydroProperties properties;
    private final SingleValueCache<RiverNetworkResponseDto> cache;

    public RiverService(SnirhLayerClient client, SnirhHydroProperties properties) {
        this.client = client;
        this.properties = properties;
        this.cache = new SingleValueCache<>(properties.riversCache(), this::load);
    }

    public RiverNetworkResponseDto getRivers() {
        return cache.get();
    }

    private RiverNetworkResponseDto load() {
        Map<String, Object> simplification = Map.of(
                "maxAllowableOffset", properties.riverSimplificationDegrees(),
                "geometryPrecision", 3);
        List<RiverFeatureDto> rivers = client.fetchAll(properties.riversUrl(), "OBJECTID,NORIOCOMP,Shape_Length", "OBJECTID", true,
                        simplification).stream()
                .map(RiverService::toRiver)
                .filter(Objects::nonNull)
                .toList();
        return RiverNetworkResponseDto.of(rivers);
    }

    static RiverFeatureDto toRiver(LayerFeatureDto feature) {
        LayerGeometryDto geometry = feature.geometry();
        Long id = feature.integer("OBJECTID");
        if (id == null || geometry == null || geometry.paths() == null) {
            return null;
        }

        double[][][] paths = Arrays.stream(geometry.paths())
                .filter(Objects::nonNull)
                .map(RiverService::round)
                .filter(path -> path.length >= 2)
                .toArray(double[][][]::new);
        if (paths.length == 0) {
            return null;
        }
        return RiverFeatureDto.of(id, feature.text("NORIOCOMP"),
                (int) Math.round(lengthKm(paths) * meanderFactor(feature.number("Shape_Length"), paths)), paths);
    }

    static double meanderFactor(Double originalLengthDegrees, double[][][] paths) {
        double simplifiedDegrees = 0;
        for (double[][] path : paths) {
            for (int i = 1; i < path.length; i++) {
                simplifiedDegrees += Math.hypot(path[i][0] - path[i - 1][0], path[i][1] - path[i - 1][1]);
            }
        }
        if (originalLengthDegrees == null || simplifiedDegrees == 0 || originalLengthDegrees <= simplifiedDegrees) {
            return 1.0;
        }
        return originalLengthDegrees / simplifiedDegrees;
    }

    static double[][] round(double[][] path) {
        List<double[]> points = new ArrayList<>(path.length);
        double[] previous = null;
        for (double[] point : path) {
            if (point == null || point.length < 2) {
                continue;
            }
            double[] rounded = {Math.round(point[0] * PRECISION) / PRECISION, Math.round(point[1] * PRECISION) / PRECISION};
            if (previous == null || previous[0] != rounded[0] || previous[1] != rounded[1]) {
                points.add(rounded);
                previous = rounded;
            }
        }
        return points.toArray(double[][]::new);
    }

    static double lengthKm(double[][][] paths) {
        double total = 0;
        for (double[][] path : paths) {
            for (int i = 1; i < path.length; i++) {
                total += haversineKm(path[i - 1], path[i]);
            }
        }
        return total;
    }

    private static double haversineKm(double[] from, double[] to) {
        double lat1 = Math.toRadians(from[1]);
        double lat2 = Math.toRadians(to[1]);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(to[0] - from[0]);
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }
}
