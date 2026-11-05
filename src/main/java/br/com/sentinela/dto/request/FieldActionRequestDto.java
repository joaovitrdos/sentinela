package br.com.sentinela.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record FieldActionRequestDto(@NotBlank @Pattern(regexp = "INSPECTION|IRRIGATION") String kind,
                                    @NotBlank @Size(max = 160) String title,
                                    @Size(max = 120) String responsibleName,
                                    @NotNull LocalDate dueDate,
                                    UUID plotId,
                                    @Size(max = 500) String sourceNote,
                                    @NotNull UUID assigneeUserId) {}
