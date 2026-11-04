package br.com.sentinela.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequestDto(

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
        String name,

        @Size(max = 512, message = "A foto deve ter no máximo 512 caracteres")
        @Pattern(regexp = "^$|^https?://\\S+$", message = "A foto deve ser um endereço http ou https")
        String picture
) {
}
