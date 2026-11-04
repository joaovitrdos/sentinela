package br.com.sentinela.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record FieldActionResponseDto(UUID actionId, UUID plotId, String kind, String title,
                                     String responsibleName, LocalDate dueDate, String status,
                                     String sourceNote, String completionNote,
                                     LocalDateTime completedAt, LocalDateTime createdAt) {}
