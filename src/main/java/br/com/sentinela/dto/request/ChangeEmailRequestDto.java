package br.com.sentinela.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ChangeEmailRequestDto(

        @NotBlank(message = "O novo e-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String newEmail,

        @NotBlank(message = "A senha atual é obrigatória")
        String currentPassword
) {
}
