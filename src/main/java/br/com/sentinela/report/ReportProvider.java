package br.com.sentinela.report;

import java.util.Map;
import java.util.UUID;

/** Descreve um relatório disponível e prepara somente os dados permitidos para ele. */
public interface ReportProvider {

    String code();

    String title();

    String templatePath();

    PreparedReport prepare(UUID userId, Map<String, Object> requestedParameters);
}
