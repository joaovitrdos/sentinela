package br.com.sentinela.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record PropertyAlertResponseDto(UUID alertId, String type, String severity, String title, String message,
                                       String disclaimer, LocalDateTime createdAt) {}
