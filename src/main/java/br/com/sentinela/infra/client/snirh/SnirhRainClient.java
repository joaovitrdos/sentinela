package br.com.sentinela.infra.client.snirh;

import br.com.sentinela.exception.SnirhApiException;
import br.com.sentinela.exception.SnirhUnavailableException;
import br.com.sentinela.dto.response.ArcGisQueryResponseDto;
import br.com.sentinela.dto.response.ArcGisQueryResponseDto.FeatureDto;
import br.com.sentinela.infra.config.SnirhProperties;
import br.com.sentinela.util.LogUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SnirhRainClient {

    private static final String FIELDS =
            "estcodigo,Nome,Bacia,Municipio,Estado,Responsavel,"
                    + "Status_Estacao,Data_ult_dado,Ult_Dado,Status_Dado";

    private final RestClient snirhRestClient;
    private final SnirhProperties properties;

    public List<FeatureDto> fetchAllFeatures() {
        List<FeatureDto> allFeatures = new ArrayList<>();
        int offset = 0;
        List<FeatureDto> page;

        do {
            page = fetchPage(offset);
            allFeatures.addAll(page);
            offset += page.size();
        } while (page.size() == properties.pageSize());

        LogUtil.info("SNIRH 24-hour rainfall: loaded {0} stations", allFeatures.size());
        return allFeatures;
    }

    private List<FeatureDto> fetchPage(int offset) {
        URI uri = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .queryParam("where", "1%3D1")
                .queryParam("outFields", FIELDS)
                .queryParam("orderByFields", "estcodigo")
                .queryParam("resultOffset", offset)
                .queryParam("resultRecordCount", properties.pageSize())
                .queryParam("outSR", 4326)
                .queryParam("f", "json")
                .build(true)
                .toUri();

        for (int attempt = 1; attempt <= properties.maxRetries(); attempt++) {
            try {
                ArcGisQueryResponseDto response = snirhRestClient.get()
                        .uri(uri)
                        .retrieve()
                        .body(ArcGisQueryResponseDto.class);

                if (response == null) {
                    throw new SnirhApiException("SNIRH returned an empty response");
                }
                if (response.error() != null) {
                    throw new SnirhApiException(
                            "ArcGIS error " + response.error().code()
                                    + ": " + response.error().message());
                }
                return response.features() != null ? response.features() : List.of();

            } catch (SnirhApiException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                LogUtil.warn("SNIRH request failed at offset {0}, attempt {1}/{2}: {3}",
                        offset, attempt, properties.maxRetries(), exception.getMessage());
                if (attempt == properties.maxRetries()) {
                    throw new SnirhUnavailableException(
                            "Failed to query SNIRH at offset " + offset, exception);
                }
                sleep(properties.retryDelayMs() * attempt);
            }
        }

        throw new SnirhUnavailableException("Could not query SNIRH");
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
