package br.com.sentinela.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TeamAcceptRequestDto(@NotBlank String token) {}
