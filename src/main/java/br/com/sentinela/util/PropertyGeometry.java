package br.com.sentinela.util;

import br.com.sentinela.dto.response.PropertyGeometryResponseDto;
import br.com.sentinela.exception.InvalidPropertyGeometryException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PropertyGeometry {

    private static final double EARTH_RADIUS_METRES = 6_371_008.8;
    private static final double CROSS_TOLERANCE = 0.000001;
    private static final double COORDINATE_TOLERANCE = 0.0000001;
    private static final int MAX_VERTICES = 200;
    private static final double MAX_AREA_HECTARES = 10_000;
    private static final double MAX_EXTENT_METRES = 50_000;

    private PropertyGeometry() {
    }

    public record Validated(
            PropertyGeometryResponseDto geometry,
            double latitude,
            double longitude,
            double areaHectares
    ) {
    }

    public static Validated validate(PropertyGeometryResponseDto geometry) {
        if (geometry == null || !"Polygon".equals(geometry.type())
                || geometry.coordinates() == null || geometry.coordinates().size() != 1) {
            throw invalid("A área deve ser um polígono GeoJSON com um único contorno, sem áreas internas vazias");
        }
        List<List<Double>> ring = geometry.coordinates().getFirst();
        if (ring == null || ring.size() < 4 || ring.size() > MAX_VERTICES + 1) {
            throw invalid("Desenhe entre 3 e 200 pontos e feche o contorno da propriedade");
        }

        List<List<Double>> points = new ArrayList<>(ring.size());
        for (List<Double> point : ring) {
            if (point == null || point.size() != 2 || point.get(0) == null || point.get(1) == null) {
                throw invalid("Cada ponto deve conter apenas longitude e latitude, nessa ordem");
            }
            double longitude = point.get(0);
            double latitude = point.get(1);
            if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                    || longitude < -180 || longitude > 180 || latitude < -85 || latitude > 85) {
                throw invalid("As coordenadas devem estar entre -180 e 180 de longitude e -85 e 85 de latitude");
            }
            points.add(List.of(longitude == 0 ? 0.0 : longitude, latitude == 0 ? 0.0 : latitude));
        }
        if (!points.getFirst().equals(points.getLast())) {
            throw invalid("O primeiro e o último ponto do contorno devem ser iguais");
        }
        int count = points.size() - 1;
        Set<List<Double>> distinct = new HashSet<>(points.subList(0, count));
        if (distinct.size() != count) {
            throw invalid("O contorno não pode repetir pontos, exceto para fechar o polígono");
        }

        double minLongitude = points.stream().mapToDouble(point -> point.get(0)).min().orElseThrow();
        double maxLongitude = points.stream().mapToDouble(point -> point.get(0)).max().orElseThrow();
        double minLatitude = points.stream().mapToDouble(point -> point.get(1)).min().orElseThrow();
        double maxLatitude = points.stream().mapToDouble(point -> point.get(1)).max().orElseThrow();
        if (maxLongitude - minLongitude > 180) {
            throw invalid("O contorno não pode atravessar o antimeridiano");
        }
        double originLongitude = (minLongitude + maxLongitude) / 2;
        double originLatitude = (minLatitude + maxLatitude) / 2;
        double longitudeScale = EARTH_RADIUS_METRES * Math.cos(Math.toRadians(originLatitude));
        double extentX = Math.toRadians(maxLongitude - minLongitude) * longitudeScale;
        double extentY = Math.toRadians(maxLatitude - minLatitude) * EARTH_RADIUS_METRES;
        if (Math.hypot(extentX, extentY) > MAX_EXTENT_METRES) {
            throw invalid("A extensão do contorno deve ser de até 50 km para o monitoramento por satélite");
        }

        double[][] projected = new double[count][2];
        for (int index = 0; index < count; index++) {
            projected[index][0] = Math.toRadians(points.get(index).get(0) - originLongitude) * longitudeScale;
            projected[index][1] = Math.toRadians(points.get(index).get(1) - originLatitude) * EARTH_RADIUS_METRES;
        }
        validateIntersections(projected);

        double twiceArea = 0;
        double centroidX = 0;
        double centroidY = 0;
        double sphericalArea = 0;
        for (int index = 0; index < count; index++) {
            int next = (index + 1) % count;
            double cross = projected[index][0] * projected[next][1] - projected[next][0] * projected[index][1];
            twiceArea += cross;
            centroidX += (projected[index][0] + projected[next][0]) * cross;
            centroidY += (projected[index][1] + projected[next][1]) * cross;
            sphericalArea += Math.toRadians(points.get(next).get(0) - points.get(index).get(0))
                    * (2 + Math.sin(Math.toRadians(points.get(index).get(1)))
                    + Math.sin(Math.toRadians(points.get(next).get(1))));
        }
        double areaSquareMetres = Math.abs(sphericalArea) * EARTH_RADIUS_METRES * EARTH_RADIUS_METRES / 2;
        if (Math.abs(twiceArea) < 2 || areaSquareMetres < 1) {
            throw invalid("O contorno deve formar uma área de pelo menos 1 m²; os pontos não podem estar todos na mesma linha");
        }
        double areaHectares = areaSquareMetres / 10_000;
        if (areaHectares > MAX_AREA_HECTARES) {
            throw invalid("A propriedade deve ter até 10.000 hectares para o monitoramento por satélite");
        }
        centroidX /= 3 * twiceArea;
        centroidY /= 3 * twiceArea;
        if (!contains(projected, centroidX, centroidY)) {
            double[] interior = interiorPoint(projected);
            centroidX = interior[0];
            centroidY = interior[1];
        }

        List<List<Double>> normalizedRing = new ArrayList<>(points.subList(0, count));
        if (twiceArea < 0) {
            Collections.reverse(normalizedRing);
        }
        normalizedRing.add(normalizedRing.getFirst());
        PropertyGeometryResponseDto normalized = new PropertyGeometryResponseDto("Polygon", List.of(List.copyOf(normalizedRing)));
        return new Validated(normalized,
                originLatitude + Math.toDegrees(centroidY / EARTH_RADIUS_METRES),
                originLongitude + Math.toDegrees(centroidX / longitudeScale),
                areaHectares);
    }

    private static void validateIntersections(double[][] points) {
        int count = points.length;
        for (int index = 0; index < count; index++) {
            double[] previous = points[(index + count - 1) % count];
            double[] current = points[index];
            double[] next = points[(index + 1) % count];
            double dot = (current[0] - previous[0]) * (next[0] - current[0])
                    + (current[1] - previous[1]) * (next[1] - current[1]);
            if (Math.abs(cross(previous, current, next)) < CROSS_TOLERANCE && dot < 0) {
                throw invalid("O contorno não pode voltar sobre o mesmo segmento");
            }
            for (int other = index + 1; other < count; other++) {
                if (other == index + 1 || (index == 0 && other == count - 1)) {
                    continue;
                }
                if (intersects(current, next, points[other], points[(other + 1) % count])) {
                    throw invalid("As linhas do contorno não podem se cruzar nem se tocar");
                }
            }
        }
    }

    private static boolean intersects(double[] a, double[] b, double[] c, double[] d) {
        double abc = cross(a, b, c);
        double abd = cross(a, b, d);
        double cda = cross(c, d, a);
        double cdb = cross(c, d, b);
        if (((abc > CROSS_TOLERANCE && abd < -CROSS_TOLERANCE)
                || (abc < -CROSS_TOLERANCE && abd > CROSS_TOLERANCE))
                && ((cda > CROSS_TOLERANCE && cdb < -CROSS_TOLERANCE)
                || (cda < -CROSS_TOLERANCE && cdb > CROSS_TOLERANCE))) {
            return true;
        }
        return (Math.abs(abc) <= CROSS_TOLERANCE && onSegment(a, b, c))
                || (Math.abs(abd) <= CROSS_TOLERANCE && onSegment(a, b, d))
                || (Math.abs(cda) <= CROSS_TOLERANCE && onSegment(c, d, a))
                || (Math.abs(cdb) <= CROSS_TOLERANCE && onSegment(c, d, b));
    }

    private static double cross(double[] a, double[] b, double[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }

    private static boolean onSegment(double[] start, double[] end, double[] point) {
        return point[0] >= Math.min(start[0], end[0]) - COORDINATE_TOLERANCE
                && point[0] <= Math.max(start[0], end[0]) + COORDINATE_TOLERANCE
                && point[1] >= Math.min(start[1], end[1]) - COORDINATE_TOLERANCE
                && point[1] <= Math.max(start[1], end[1]) + COORDINATE_TOLERANCE;
    }

    private static boolean contains(double[][] points, double x, double y) {
        boolean inside = false;
        for (int index = 0, previous = points.length - 1; index < points.length; previous = index++) {
            double[] a = points[index];
            double[] b = points[previous];
            if ((a[1] > y) != (b[1] > y)
                    && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static double[] interiorPoint(double[][] points) {
        List<Double> heights = new ArrayList<>();
        for (double[] point : points) {
            heights.add(point[1]);
        }
        Collections.sort(heights);
        double gap = -1;
        double y = 0;
        for (int index = 0; index + 1 < heights.size(); index++) {
            double currentGap = heights.get(index + 1) - heights.get(index);
            if (currentGap > gap) {
                gap = currentGap;
                y = (heights.get(index) + heights.get(index + 1)) / 2;
            }
        }
        List<Double> intersections = new ArrayList<>();
        // A scan line between vertex heights avoids selecting a horizontal boundary as the interior point.
        for (int index = 0; index < points.length; index++) {
            double[] a = points[index];
            double[] b = points[(index + 1) % points.length];
            if ((a[1] > y) != (b[1] > y)) {
                intersections.add(a[0] + (y - a[1]) * (b[0] - a[0]) / (b[1] - a[1]));
            }
        }
        Collections.sort(intersections);
        double widest = -1;
        double x = 0;
        for (int index = 0; index + 1 < intersections.size(); index += 2) {
            double width = intersections.get(index + 1) - intersections.get(index);
            if (width > widest) {
                widest = width;
                x = (intersections.get(index) + intersections.get(index + 1)) / 2;
            }
        }
        if (widest <= 0) {
            throw invalid("Não foi possível determinar um ponto no interior da propriedade; revise o contorno");
        }
        return new double[]{x, y};
    }

    private static InvalidPropertyGeometryException invalid(String message) {
        return new InvalidPropertyGeometryException(message);
    }
}
