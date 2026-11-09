package br.com.sentinela.service;

import br.com.sentinela.util.Util;

import br.com.sentinela.dto.response.CarPropertyResponseDto;
import br.com.sentinela.dto.response.PropertyGeometryResponseDto;
import br.com.sentinela.util.LogUtil;
import br.com.sentinela.util.PropertyGeometry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Consulta o limite do imóvel na camada pública do SICAR, que não tem contrato de estabilidade publicado. */
@Service
public class CarService {
    private static final Pattern CODE = Pattern.compile("[A-Z]{2}-\\d{7}-[0-9A-F]{32}");
    private static final Set<String> STATES = Set.of("AC", "AL", "AM", "AP", "BA", "CE", "DF", "ES", "GO", "MA", "MG", "MS",
            "MT", "PA", "PB", "PE", "PI", "PR", "RJ", "RN", "RO", "RR", "RS", "SC", "SE", "SP", "TO");
    private static final int MAX_POINTS = 200;
    private static final String SOURCE = "SICAR - Cadastro Ambiental Rural";

    private final RestClient client;

    public CarService(@Qualifier("carRestClient") RestClient client) {
        this.client = client;
    }

    public CarPropertyResponseDto find(String rawCode) {
        String code = normalize(rawCode);
        JsonNode features = request(code).path("features");
        if (Util.isNull(features) || features.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imóvel não encontrado no CAR");
        }
        return response(features.get(0));
    }

    static String normalize(String rawCode) {
        String code = Util.isNull(rawCode) ? "" : rawCode.trim().toUpperCase(Locale.ROOT).replace(".", "");
        if (!CODE.matcher(code).matches() || !STATES.contains(code.substring(0, 2))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código do CAR inválido");
        }
        return code;
    }

    private JsonNode request(String code) {
        String layer = "sicar:sicar_imoveis_" + code.substring(0, 2).toLowerCase(Locale.ROOT);
        try {
            JsonNode response = client.get()
                    .uri(uri -> uri.queryParam("service", "WFS")
                            .queryParam("version", "2.0.0")
                            .queryParam("request", "GetFeature")
                            .queryParam("typeNames", layer)
                            .queryParam("outputFormat", "application/json")
                            .queryParam("count", 1)
                            .queryParam("cql_filter", "{filter}")
                            .build("cod_imovel='" + code + "'"))
                    .retrieve()
                    .body(JsonNode.class);
            if (Util.isNull(response)) throw new RestClientException("Resposta vazia do SICAR");
            return response;
        } catch (RestClientException exception) {
            LogUtil.warn("SICAR indisponível");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Consulta ao CAR indisponível. Tente novamente em instantes");
        }
    }

    static CarPropertyResponseDto response(JsonNode feature) {
        JsonNode geometry = feature.path("geometry");
        List<List<List<Double>>> rings = new ArrayList<>();
        if ("Polygon".equals(geometry.path("type").asText())) {
            rings.add(ring(geometry.path("coordinates").path(0)));
        } else {
            for (JsonNode polygon : geometry.path("coordinates")) rings.add(ring(polygon.path(0)));
        }
        List<List<Double>> largest = rings.stream().max((a, b) -> Double.compare(extent(a), extent(b)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "O imóvel não tem limite cadastrado no CAR"));
        List<List<Double>> simplified = simplify(largest);
        var validated = PropertyGeometry.validate(new PropertyGeometryResponseDto("Polygon", List.of(simplified)));
        JsonNode properties = feature.path("properties");
        return new CarPropertyResponseDto(properties.path("cod_imovel").asText(), properties.path("status_imovel").asText(),
                properties.path("condicao").asText(), properties.path("tipo_imovel").asText(), properties.path("municipio").asText(),
                properties.path("uf").asText(), properties.path("area").asDouble(), validated.geometry(),
                simplified.size() < largest.size(), rings.size() - 1, SOURCE);
    }

    private static List<List<Double>> ring(JsonNode coordinates) {
        Set<List<Double>> points = new LinkedHashSet<>();
        for (JsonNode point : coordinates) points.add(List.of(point.path(0).asDouble(), point.path(1).asDouble()));
        List<List<Double>> ring = new ArrayList<>(points);
        if (!Util.isNullOrEmpty(ring)) ring.add(ring.getFirst());
        return ring;
    }

    private static double extent(List<List<Double>> ring) {
        double minX = ring.stream().mapToDouble(point -> point.get(0)).min().orElse(0);
        double maxX = ring.stream().mapToDouble(point -> point.get(0)).max().orElse(0);
        double minY = ring.stream().mapToDouble(point -> point.get(1)).min().orElse(0);
        double maxY = ring.stream().mapToDouble(point -> point.get(1)).max().orElse(0);
        return (maxX - minX) * (maxY - minY);
    }

    /** Douglas-Peucker com tolerância crescente, até caber no limite de pontos aceito pelas propriedades. */
    static List<List<Double>> simplify(List<List<Double>> ring) {
        List<List<Double>> result = ring;
        for (double tolerance = 0.000005; result.size() > MAX_POINTS + 1; tolerance *= 1.5) {
            boolean[] keep = new boolean[ring.size()];
            keep[0] = true;
            keep[ring.size() - 1] = true;
            mark(ring, 0, ring.size() - 1, tolerance, keep);
            List<List<Double>> kept = new ArrayList<>();
            for (int index = 0; index < ring.size(); index++) {
                if (keep[index]) kept.add(ring.get(index));
            }
            result = kept;
        }
        return result;
    }

    private static void mark(List<List<Double>> ring, int start, int end, double tolerance, boolean[] keep) {
        int farthest = -1;
        double distance = tolerance;
        for (int index = start + 1; index < end; index++) {
            double current = distance(ring.get(index), ring.get(start), ring.get(end));
            if (current > distance) {
                distance = current;
                farthest = index;
            }
        }
        if (farthest < 0) return;
        keep[farthest] = true;
        mark(ring, start, farthest, tolerance, keep);
        mark(ring, farthest, end, tolerance, keep);
    }

    private static double distance(List<Double> point, List<Double> start, List<Double> end) {
        double dx = end.get(0) - start.get(0);
        double dy = end.get(1) - start.get(1);
        double length = Math.hypot(dx, dy);
        if (length == 0) return Math.hypot(point.get(0) - start.get(0), point.get(1) - start.get(1));
        return Math.abs(dy * (point.get(0) - start.get(0)) - dx * (point.get(1) - start.get(1))) / length;
    }
}
