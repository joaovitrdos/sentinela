package br.com.sentinela.service;

import br.com.sentinela.infra.cache.SingleValueCache;
import br.com.sentinela.util.LogUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Avisos meteorológicos do INMET; "Perigo Potencial" fica de fora porque é frequente demais para virar e-mail. */
@Service
public class InmetService {
    private static final Set<String> RELEVANT_SEVERITIES = Set.of("Perigo", "Grande Perigo");

    private final SingleValueCache<List<InmetWarning>> cache;

    public record InmetWarning(long id, String event, String severity, String startsAt, String endsAt, String risk,
                               List<List<double[]>> rings) {

        public boolean covers(double latitude, double longitude) {
            return rings.stream().anyMatch(ring -> contains(ring, latitude, longitude));
        }

        private static boolean contains(List<double[]> ring, double latitude, double longitude) {
            boolean inside = false;
            for (int index = 0, previous = ring.size() - 1; index < ring.size(); previous = index++) {
                double[] a = ring.get(index);
                double[] b = ring.get(previous);
                if ((a[1] > latitude) != (b[1] > latitude)
                        && longitude < (b[0] - a[0]) * (latitude - a[1]) / (b[1] - a[1]) + a[0]) {
                    inside = !inside;
                }
            }
            return inside;
        }
    }

    public InmetService(@Qualifier("inmetRestClient") RestClient client, ObjectMapper mapper) {
        this.cache = new SingleValueCache<>(Duration.ofMinutes(30),
                () -> parse(client.get().retrieve().body(JsonNode.class), mapper));
    }

    public List<InmetWarning> activeWarnings() {
        return cache.get();
    }

    static List<InmetWarning> parse(JsonNode response, ObjectMapper mapper) {
        Map<Long, InmetWarning> warnings = new LinkedHashMap<>();
        for (String section : List.of("hoje", "futuro")) {
            for (JsonNode warning : response.path(section)) {
                if (warning.path("encerrado").asBoolean() || !RELEVANT_SEVERITIES.contains(warning.path("severidade").asText())) continue;
                try {
                    long id = warning.path("id_aviso").asLong();
                    warnings.put(id, new InmetWarning(id, warning.path("descricao").asText(), warning.path("severidade").asText(),
                            warning.path("inicio").asText(), warning.path("fim").asText(),
                            warning.path("riscos").path(0).asText(""), rings(mapper.readTree(warning.path("poligono").asText()))));
                } catch (RuntimeException exception) {
                    LogUtil.warn("Aviso do INMET ignorado por polígono inválido");
                }
            }
        }
        return List.copyOf(warnings.values());
    }

    private static List<List<double[]>> rings(JsonNode geometry) {
        List<List<double[]>> rings = new ArrayList<>();
        if ("Polygon".equals(geometry.path("type").asText())) {
            rings.add(ring(geometry.path("coordinates").path(0)));
        } else {
            for (JsonNode polygon : geometry.path("coordinates")) rings.add(ring(polygon.path(0)));
        }
        return rings;
    }

    private static List<double[]> ring(JsonNode coordinates) {
        List<double[]> ring = new ArrayList<>();
        for (JsonNode point : coordinates) ring.add(new double[]{point.path(0).asDouble(), point.path(1).asDouble()});
        return ring;
    }
}
