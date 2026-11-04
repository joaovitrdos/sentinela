package br.com.sentinela.dto.request;

import jakarta.validation.constraints.NotNull;

public record TeamModeRequestDto(@NotNull Boolean enabled) {}
