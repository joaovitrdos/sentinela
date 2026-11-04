package br.com.sentinela.infra.client.snirh;

import br.com.sentinela.exception.SnirhApiException;
import br.com.sentinela.exception.SnirhUnavailableException;
import br.com.sentinela.dto.response.ArcGisLayerResponseDto;
import br.com.sentinela.dto.response.ArcGisLayerResponseDto.LayerFeatureDto;
import br.com.sentinela.infra.config.SnirhHydroProperties;
import br.com.sentinela.util.LogUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

@Component
@RequiredArgsConstructor
public class SnirhLayerClient {

    private final RestClient snirhRestClient;
    private final SnirhHydroProperties properties;

    public List<LayerFeatureDto> fetchAll(String queryUrl, String fields, String orderBy, boolean withGeometry) {
        return fetchAll(queryUrl, fields, orderBy, withGeometry, Map.of());
    }

    public List<LayerFeatureDto> fetchAll(String queryUrl, String fields, String orderBy, boolean withGeometry,
                                          Map<String, Object> extraParams) {
        int total = count(queryUrl);
        if (total == 0) {
            return List.of();
        }

        int pageSize = properties.pageSize();
        Semaphore permits = new Semaphore(Math.max(1, properties.parallelPages()));
        List<LayerFeatureDto> all = new ArrayList<>(total);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<LayerFeatureDto>>> pages = new ArrayList<>();
            for (int offset = 0; offset < total; offset += pageSize) {
                int pageOffset = offset;
                pages.add(executor.submit(() -> {
                    permits.acquire();
                    try {
                        return fetchPage(queryUrl, fields, orderBy, withGeometry, extraParams, pageOffset);
                    } finally {
                        permits.release();
                    }
                }));
            }
            for (Future<List<LayerFeatureDto>> page : pages) {
                all.addAll(await(page));
            }
        }

        LogUtil.info("SNIRH {0}: loaded {1} records", queryUrl, all.size());
        return all;
    }

    private int count(String queryUrl) {
        URI uri = UriComponentsBuilder.fromUriString(queryUrl)
                .queryParam("where", "1%3D1")
                .queryParam("returnCountOnly", true)
                .queryParam("f", "json")
                .build(true)
                .toUri();
        ArcGisLayerResponseDto response = call(uri, "count");
        return response.total() != null ? response.total() : 0;
    }

    private List<LayerFeatureDto> fetchPage(String queryUrl, String fields, String orderBy, boolean withGeometry,
                                            Map<String, Object> extraParams, int offset) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(queryUrl)
                .queryParam("where", "1%3D1")
                .queryParam("outFields", fields)
                .queryParam("orderByFields", orderBy)
                .queryParam("resultOffset", offset)
                .queryParam("resultRecordCount", properties.pageSize())
                .queryParam("returnGeometry", withGeometry)
                .queryParam("outSR", 4326)
                .queryParam("f", "json");
        extraParams.forEach(builder::queryParam);
        URI uri = builder.build(true).toUri();
        ArcGisLayerResponseDto response = call(uri, "offset " + offset);
        return response.features() != null ? response.features() : List.of();
    }

    private ArcGisLayerResponseDto call(URI uri, String context) {
        for (int attempt = 1; attempt <= properties.maxRetries(); attempt++) {
            try {
                ArcGisLayerResponseDto response = snirhRestClient.get().uri(uri).retrieve().body(ArcGisLayerResponseDto.class);
                if (response == null) {
                    throw new SnirhApiException("SNIRH returned an empty response");
                }
                if (response.error() != null) {
                    throw new SnirhApiException("ArcGIS error " + response.error().code() + ": " + response.error().message());
                }
                return response;
            } catch (SnirhApiException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                LogUtil.warn("SNIRH request failed ({0}), attempt {1}/{2}: {3}",
                        context, attempt, properties.maxRetries(), exception.getMessage());
                if (attempt == properties.maxRetries()) {
                    throw new SnirhUnavailableException("Failed to query SNIRH (" + context + ")", exception);
                }
                sleep(properties.retryDelayMs() * attempt);
            }
        }
        throw new SnirhUnavailableException("Could not query SNIRH");
    }

    private static List<LayerFeatureDto> await(Future<List<LayerFeatureDto>> page) {
        try {
            return page.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SnirhUnavailableException("SNIRH request was interrupted", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new SnirhUnavailableException("Failed to query SNIRH", exception.getCause());
        }
    }

    private static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SnirhUnavailableException("SNIRH request was interrupted", exception);
        }
    }
}
