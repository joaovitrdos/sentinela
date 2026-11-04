package br.com.sentinela.dto.request;

import br.com.sentinela.dto.response.PropertyGeometryResponseDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PropertyRequestDto(
        @NotBlank(message = "O nome da propriedade é obrigatório")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
        String name,

        @NotBlank(message = "A cidade é obrigatória")
        @Size(max = 120, message = "A cidade deve ter no máximo 120 caracteres")
        String city,

        @NotBlank(message = "O estado é obrigatório")
        @Size(max = 80, message = "O estado deve ter no máximo 80 caracteres")
        String state,

        @NotNull(message = "Desenhe o limite da propriedade no mapa")
        PropertyGeometryResponseDto geometry,

        @Size(max = 120, message = "A cultura deve ter no máximo 120 caracteres")
        String cropName,

        @Size(max = 120, message = "O nome do talhão deve ter no máximo 120 caracteres")
        String plotName,

        @PastOrPresent(message = "A data de plantio não pode ser futura")
        LocalDate plantingDate,

        @Size(max = 80, message = "A fase deve ter no máximo 80 caracteres")
        String cropStage
) {
}
