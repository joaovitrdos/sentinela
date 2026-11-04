package br.com.sentinela.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ValidateCodeRequestDto(

        @NotBlank(message = "O código é obrigatório")
        @Pattern(regexp = "\\d{8}", message = "O código deve ter 8 dígitos")
        String code,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email
) {
}
