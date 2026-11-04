package br.com.sentinela.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamTitleRequestDto(@NotBlank @Size(max = 120) String title) {}
