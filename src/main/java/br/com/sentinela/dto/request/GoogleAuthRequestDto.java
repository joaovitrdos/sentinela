package br.com.sentinela.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GoogleAuthRequestDto(

        @NotBlank(message = "O token do Google é obrigatório")
        String idToken
) {
}
