package br.com.sentinela.dto.response;

import org.springframework.http.MediaType;

public record GeneratedReportResposneDto(byte[] content, String filename, MediaType mediaType) {
}
