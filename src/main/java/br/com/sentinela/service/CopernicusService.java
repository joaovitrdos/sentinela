package br.com.sentinela.service;

import br.com.sentinela.util.Util;

import br.com.sentinela.util.LogUtil;

import br.com.sentinela.dto.response.PropertyGeometryResponseDto;
import br.com.sentinela.dto.response.PropertyMonitoringDto;
import br.com.sentinela.dto.response.PropertyMonitoringDto.Observation;
import br.com.sentinela.entity.Property;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Service
public class CopernicusService {

    private static final String SOURCE = "Copernicus Sentinel-2 L2A";
    private static final double EARTH_RADIUS = 6378137.0;
    private static final String CRS = "http://www.opengis.net/def/crs/EPSG/0/3857";

    private static final String STATISTICS_SCRIPT = """
            //VERSION=3
            function setup() {
              return {
                input: [{ bands: ["B04", "B08", "SCL", "dataMask"] }],
                output: [
                  { id: "ndvi", bands: 1, sampleType: "FLOAT32" },
                  { id: "dataMask", bands: 1 }
                ]
              };
            }
            function evaluatePixel(s) {
              const sum = s.B08 + s.B04;
              const clear = s.SCL === 4 || s.SCL === 5 || s.SCL === 6;
              const valid = s.dataMask === 1 && clear && sum > 0 ? 1 : 0;
              return {
                ndvi: [valid ? (s.B08 - s.B04) / sum : 0],
                dataMask: [valid]
              };
            }
            """;

    private static final String IMAGE_SCRIPT = """
            //VERSION=3
            function setup() {
              return {
                input: [{ bands: ["B04", "B08", "SCL", "dataMask"] }],
                output: { bands: 4, sampleType: "AUTO" }
              };
            }
            const ramp = [
              [-1.0, [0.55, 0.16, 0.16]],
              [ 0.0, [0.78, 0.26, 0.16]],
              [ 0.2, [0.94, 0.54, 0.18]],
              [ 0.4, [0.97, 0.83, 0.27]],
              [ 0.6, [0.40, 0.71, 0.26]],
              [ 0.8, [0.08, 0.47, 0.23]],
              [ 1.0, [0.02, 0.25, 0.14]]
            ];
            function evaluatePixel(s) {
              const sum = s.B08 + s.B04;
              const clear = s.SCL === 4 || s.SCL === 5 || s.SCL === 6;
              if (s.dataMask !== 1 || !clear || sum <= 0) return [0, 0, 0, 0];
              const ndvi = Math.max(-1, Math.min(1, (s.B08 - s.B04) / sum));
              for (let i = 1; i < ramp.length; i++) {
                if (ndvi <= ramp[i][0]) {
                  const t = (ndvi - ramp[i - 1][0]) / (ramp[i][0] - ramp[i - 1][0]);
                  return ramp[i - 1][1].map((v, c) => v + (ramp[i][1][c] - v) * t).concat([1]);
                }
              }
              return ramp[ramp.length - 1][1].concat([1]);
            }
            """;

    private final PropertyService propertyService;
    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String tokenUrl;
    private final String processUrl;
    private final String statisticsUrl;
    private final int imageMaxSize;
    private final Cache<MonitoringKey, PropertyMonitoringDto> monitoringCache;
    private final Cache<ImageKey, byte[]> imageCache;
    private final Semaphore providerSlots = new Semaphore(2, true);
    private volatile AccessToken accessToken;

    public CopernicusService(
            PropertyService propertyService,
            RestClient.Builder restClientBuilder,
            @Value("${copernicus.client-id:}") String clientId,
            @Value("${copernicus.client-secret:}") String clientSecret,
            @Value("${copernicus.token-url}") String tokenUrl,
            @Value("${copernicus.process-url}") String processUrl,
            @Value("${copernicus.statistics-url}") String statisticsUrl,
            @Value("${copernicus.connect-timeout:10s}") String connectTimeout,
            @Value("${copernicus.request-timeout:60s}") String requestTimeout,
            @Value("${copernicus.cache:6h}") String cacheDuration,
            @Value("${copernicus.image-max-size:512}") int imageMaxSize) {
        this.propertyService = propertyService;
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
        this.tokenUrl = tokenUrl;
        this.processUrl = processUrl;
        this.statisticsUrl = statisticsUrl;
        this.imageMaxSize = Math.max(128, Math.min(1024, imageMaxSize));
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(DurationStyle.detectAndParse(connectTimeout));
        factory.setReadTimeout(DurationStyle.detectAndParse(requestTimeout));
        this.restClient = restClientBuilder.clone().requestFactory(factory).build();
        this.monitoringCache = Caffeine.newBuilder().maximumSize(128)
                .expireAfterWrite(DurationStyle.detectAndParse(cacheDuration)).build();
        this.imageCache = Caffeine.newBuilder().maximumWeight(32L * 1024 * 1024)
                .weigher((ImageKey key, byte[] bytes) -> bytes.length)
                .expireAfterWrite(DurationStyle.detectAndParse(cacheDuration)).build();
    }

    public PropertyMonitoringDto monitor(UUID propertyId, UUID ownerId, LocalDate from, LocalDate to, int cloud) {
        Property property = propertyService.requireView(propertyId, ownerId);
        LocalDate end = Util.isNull(to) ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = Util.isNull(from) ? end.minusDays(29) : from;
        validateRange(start, end, cloud);
        PropertyGeometryResponseDto geometry = propertyService.geometryOf(property);
        if (Util.isNull(geometry)) {
            return emptyResponse("BOUNDARY_REQUIRED", "Desenhe e salve o limite da propriedade para monitorar a vegetação.",
                    start, end, null);
        }
        Area area = area(geometry);
        if (!configured()) {
            return emptyResponse("NOT_CONFIGURED", "Configure as credenciais do Copernicus no servidor para consultar imagens e NDVI reais.",
                    start, end, area);
        }
        MonitoringKey key = new MonitoringKey(propertyId, property.getPolygonJson(), start, end, cloud);
        return monitoringCache.get(key, ignored -> requestMonitoring(area, start, end, cloud));
    }

    public byte[] ndviImage(UUID propertyId, UUID ownerId, LocalDate date, int cloud) {
        Property property = propertyService.requireView(propertyId, ownerId);
        validateRange(date, date, cloud);
        PropertyGeometryResponseDto geometry = propertyService.geometryOf(property);
        if (Util.isNull(geometry)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Salve o limite da propriedade antes de consultar o NDVI.");
        }
        if (!configured()) {
            throw unavailable("Configure as credenciais do Copernicus no servidor.");
        }
        Area area = area(geometry);
        ImageKey key = new ImageKey(propertyId, property.getPolygonJson(), date, cloud);
        return imageCache.get(key, ignored -> requestImage(area, date, cloud));
    }

    private PropertyMonitoringDto requestMonitoring(Area area, LocalDate from, LocalDate to, int cloud) {
        Map<String, Object> aggregation = Map.of(
                "timeRange", timeRange(from, to),
                "aggregationInterval", Map.of("of", "P1D"),
                "evalscript", STATISTICS_SCRIPT,
                "width", area.width(), "height", area.height());
        Map<String, Object> payload = Map.of("input", input(area, from, to, cloud), "aggregation", aggregation);
        JsonNode response = providerRequest(() -> restClient.post().uri(statisticsUrl)
                .headers(headers -> headers.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .body(payload).retrieve().body(JsonNode.class));
        if (Util.isNull(response) || !response.path("data").isArray()
                || !("OK".equals(response.path("status").asText())
                || "PARTIAL".equals(response.path("status").asText()))) {
            throw badGateway("O Copernicus não conseguiu calcular os indicadores para este período.");
        }
        List<Observation> observations = new ArrayList<>();
        long geometryPixels = response.path("geometryPixelCount").asLong();
        boolean partial = "PARTIAL".equals(response.path("status").asText());
        for (JsonNode interval : response.path("data")) {
            if (interval.has("error")) {
                partial = true;
                continue;
            }
            JsonNode stats = interval.path("outputs").path("ndvi").path("bands").path("B0").path("stats");
            Double mean = finiteNumber(stats.path("mean"));
            Double min = finiteNumber(stats.path("min"));
            Double max = finiteNumber(stats.path("max"));
            long samples = stats.path("sampleCount").asLong();
            long excluded = stats.path("noDataCount").asLong();
            if (Util.isNull(mean) || Util.isNull(min) || Util.isNull(max) || samples <= excluded || mean < -1 || mean > 1) {
                continue;
            }
            String date = interval.path("interval").path("from").asText();
            if (date.length() < 10) {
                partial = true;
                continue;
            }
            Double validPixelPercentage = geometryPixels > 0
                    ? Math.max(0, Math.min(100, (samples - excluded) * 100.0 / geometryPixels))
                    : null;
            LocalDate observationDate;
            try {
                observationDate = LocalDate.parse(date.substring(0, 10));
            } catch (DateTimeParseException exception) {
                throw badGateway("O Copernicus retornou uma data de observação inválida.");
            }
            observations.add(new Observation(observationDate, mean, min, max,
                    validPixelPercentage));
        }
        observations.sort(Comparator.comparing(Observation::date));
        if (Util.isNullOrEmpty(observations)) {
            if (partial) {
                throw badGateway("O Copernicus não concluiu o processamento deste período. Tente um intervalo menor.");
            }
            return emptyResponse("NO_DATA", "Nenhuma observação válida no período. Nuvens ou ausência de passagem podem impedir a análise; amplie o período ou ajuste o filtro de nuvens.",
                    from, to, area);
        }
        Observation latest = observations.getLast();
        Observation previous = observations.size() > 1 ? observations.get(observations.size() - 2) : null;
        if (Util.nonNull(latest.validPixelPercentage()) && latest.validPixelPercentage() < 50) {
            partial = true;
        }
        String message = partial
                ? "Análise parcial: há limitações de qualidade ou pixels úteis. Compare as datas considerando as nuvens."
                : "NDVI calculado somente dentro do limite salvo. Pixels úteis indicam a proporção dos pixels do contorno com dados válidos após excluir nuvens, sombras e outros pixels inadequados.";
        return new PropertyMonitoringDto(true, partial ? "PARTIAL" : "READY", message, from, to,
                area.bounds(), area.resolutionMeters(), List.copyOf(observations), latest.date(),
                Util.isNull(previous) ? null : previous.date(), Util.isNull(previous) ? null : previous.meanNdvi(),
                Util.isNull(previous) ? null : latest.meanNdvi() - previous.meanNdvi(), SOURCE);
    }

    private byte[] requestImage(Area area, LocalDate date, int cloud) {
        Map<String, Object> payload = Map.of(
                "input", input(area, date, date, cloud),
                "output", Map.of("width", area.width(), "height", area.height(),
                        "responses", List.of(Map.of("identifier", "default", "format", Map.of("type", "image/png")))),
                "evalscript", IMAGE_SCRIPT);
        byte[] png = providerRequest(() -> restClient.post().uri(processUrl)
                .headers(headers -> headers.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.IMAGE_PNG)
                .body(payload).retrieve().body(byte[].class));
        if (Util.isNull(png) || png.length < 8 || (png[0] & 0xff) != 137 || png[1] != 80 || png[2] != 78 || png[3] != 71) {
            throw badGateway("O Copernicus não retornou uma imagem NDVI válida.");
        }
        return png;
    }

    private Map<String, Object> input(Area area, LocalDate from, LocalDate to, int cloud) {
        return Map.of(
                "bounds", Map.of("geometry", area.projectedGeometry(), "properties", Map.of("crs", CRS)),
                "data", List.of(Map.of("type", "sentinel-2-l2a",
                        "dataFilter", Map.of("timeRange", timeRange(from, to), "maxCloudCoverage", cloud,
                                "mosaickingOrder", "mostRecent"))));
    }

    private Map<String, String> timeRange(LocalDate from, LocalDate to) {
        return Map.of("from", from.atStartOfDay().toInstant(ZoneOffset.UTC).toString(),
                "to", to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toString());
    }

    private Area area(PropertyGeometryResponseDto geometry) {
        double west = Double.POSITIVE_INFINITY, south = Double.POSITIVE_INFINITY;
        double east = Double.NEGATIVE_INFINITY, north = Double.NEGATIVE_INFINITY;
        List<List<List<Double>>> projectedRings = new ArrayList<>();
        for (List<List<Double>> ring : geometry.coordinates()) {
            List<List<Double>> projected = new ArrayList<>();
            for (List<Double> position : ring) {
                double longitude = position.get(0), latitude = position.get(1);
                if (Math.abs(latitude) > 85) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O monitoramento atende áreas entre as latitudes 85°S e 85°N.");
                }
                west = Math.min(west, longitude);
                east = Math.max(east, longitude);
                south = Math.min(south, latitude);
                north = Math.max(north, latitude);
                projected.add(List.of(mercatorX(longitude), mercatorY(latitude)));
            }
            projectedRings.add(List.copyOf(projected));
        }
        double widthMeters = mercatorX(east) - mercatorX(west);
        double heightMeters = mercatorY(north) - mercatorY(south);
        // Web Mercator metres are scaled by latitude. Keep nominal ground sampling at 10 m where possible.
        double latitudeScale = Math.cos(Math.toRadians((south + north) / 2));
        double pixelSize = Math.max(10 / latitudeScale, Math.max(widthMeters, heightMeters) / imageMaxSize);
        int width = Math.max(1, Math.min(imageMaxSize, (int) Math.ceil(widthMeters / pixelSize)));
        int height = Math.max(1, Math.min(imageMaxSize, (int) Math.ceil(heightMeters / pixelSize)));
        double groundResolution = Math.max(widthMeters / width, heightMeters / height) * latitudeScale;
        return new Area(Map.of("type", "Polygon", "coordinates", projectedRings),
                List.of(west, south, east, north), width, height, groundResolution);
    }

    private double mercatorX(double longitude) {
        return EARTH_RADIUS * Math.toRadians(longitude);
    }

    private double mercatorY(double latitude) {
        return EARTH_RADIUS * Math.log(Math.tan(Math.PI / 4 + Math.toRadians(latitude) / 2));
    }

    private void validateRange(LocalDate from, LocalDate to, int cloud) {
        if (Util.isNull(from) || Util.isNull(to) || from.isAfter(to) || to.isAfter(LocalDate.now(ZoneOffset.UTC))
                || ChronoUnit.DAYS.between(from, to) >= 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um período de até 90 dias, sem datas futuras.");
        }
        if (cloud < 0 || cloud > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O limite de nuvens deve estar entre 0 e 100%.");
        }
    }

    private boolean configured() {
        return !Util.isNullOrBlank(clientId) && !Util.isNullOrBlank(clientSecret);
    }

    private PropertyMonitoringDto emptyResponse(String status, String message, LocalDate from, LocalDate to, Area area) {
        return new PropertyMonitoringDto(configured(), status, message, from, to,
                Util.isNull(area) ? List.of() : area.bounds(), Util.isNull(area) ? null : area.resolutionMeters(),
                List.of(), null, null, null, null, SOURCE);
    }

    private <T> T providerRequest(Supplier<T> request) {
        if (!providerSlots.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Há consultas de satélite em andamento. Aguarde alguns instantes e tente novamente.");
        }
        try {
            return executeProviderRequest(request);
        } finally {
            providerSlots.release();
        }
    }

    private <T> T executeProviderRequest(Supplier<T> request) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return request.get();
            } catch (RestClientResponseException exception) {
                int status = exception.getStatusCode().value();
                if (status == 401 && attempt == 0) {
                    accessToken = null;
                    continue;
                }
                LogUtil.warn("Copernicus respondeu HTTP {0}", status);
                if (status == 429) {
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                            "O limite de consultas ou processamento do Copernicus foi atingido. Aguarde e consulte a cota da conta.");
                }
                if (status == 401 || status == 403) {
                    throw unavailable("O Copernicus recusou o acesso. Confira as credenciais e as permissões da conta no servidor.");
                }
                throw badGateway("O Copernicus está indisponível para esta consulta. Tente novamente mais tarde.");
            } catch (ResourceAccessException exception) {
                LogUtil.warn("Copernicus sem resposta", exception);
                throw unavailable("A consulta ao Copernicus demorou mais que o esperado ou não houve conexão. Tente novamente.");
            } catch (RestClientException exception) {
                throw badGateway("Não foi possível interpretar a resposta do Copernicus.");
            }
        }
        throw unavailable("Não foi possível autenticar no Copernicus.");
    }

    private synchronized String token() {
        AccessToken cached = accessToken;
        if (Util.nonNull(cached) && Instant.now().isBefore(cached.expiresAt())) {
            return cached.value();
        }
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        JsonNode response;
        try {
            response = restClient.post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON).body(form).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 429) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "O limite de autenticação do Copernicus foi atingido. Aguarde antes de tentar novamente.");
            }
            throw unavailable("Não foi possível autenticar no Copernicus. Confira o Client ID e o Client Secret no servidor.");
        } catch (RestClientException exception) {
            throw unavailable("Não foi possível conectar ao serviço de autenticação do Copernicus.");
        }
        if (Util.isNull(response) || Util.isNullOrBlank(response.path("access_token").asText())) {
            throw unavailable("O Copernicus não retornou uma autenticação válida.");
        }
        long expiresIn = Math.max(1, response.path("expires_in").asLong(300));
        long safetyWindow = Math.min(60, Math.max(1, expiresIn / 10));
        accessToken = new AccessToken(response.path("access_token").asText(),
                Instant.now().plusSeconds(Math.max(1, expiresIn - safetyWindow)));
        return accessToken.value();
    }

    private Double finiteNumber(JsonNode value) {
        if (!value.isNumber()) {
            return null;
        }
        double number = value.doubleValue();
        return Double.isFinite(number) ? number : null;
    }

    private ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    private ResponseStatusException badGateway(String message) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }

    private record AccessToken(String value, Instant expiresAt) { }
    private record MonitoringKey(UUID propertyId, String polygon, LocalDate from, LocalDate to, int cloud) { }
    private record ImageKey(UUID propertyId, String polygon, LocalDate date, int cloud) { }
    private record Area(Map<String, Object> projectedGeometry, List<Double> bounds, int width, int height,
                        double resolutionMeters) { }
}
