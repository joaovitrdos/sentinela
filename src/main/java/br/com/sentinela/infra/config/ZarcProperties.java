package br.com.sentinela.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Arquivos da Tábua de risco do ZARC (dados abertos do MAPA). O portal recusa clientes sem identificação, por isso o
 * importador se apresenta com um User-Agent próprio e honesto.
 */
@ConfigurationProperties(prefix = "public-data.zarc")
public record ZarcProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("Sentinela-Importador/1.0") String userAgent,
        @DefaultValue("10m") Duration timeout,
        List<SourceFile> files
) {
    public record SourceFile(String harvest, String url) {}

    @Configuration
    @EnableConfigurationProperties(ZarcProperties.class)
    static class Registration {}
}
