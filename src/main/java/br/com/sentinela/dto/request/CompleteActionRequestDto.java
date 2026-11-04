package br.com.sentinela.dto.request;

import jakarta.validation.constraints.*;

public record CompleteActionRequestDto(@NotBlank @Size(max = 1000) String completionNote,
                                       @PositiveOrZero Double amountMm) {}
