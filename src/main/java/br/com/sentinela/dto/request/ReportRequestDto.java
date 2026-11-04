package br.com.sentinela.dto.request;

import java.util.Map;

public record ReportRequestDto(String format, Map<String, Object> parameters) {
}
