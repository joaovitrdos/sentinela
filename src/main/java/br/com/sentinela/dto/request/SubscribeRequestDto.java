package br.com.sentinela.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SubscribeRequestDto(
        @NotBlank(message = "Escolha um plano") String planCode,
        @NotBlank(message = "Escolha a periodicidade")
        @Pattern(regexp = "MONTHLY|YEARLY", message = "A periodicidade deve ser mensal ou anual") String cycle,
        @Pattern(regexp = "[0-9./-]{11,18}", message = "CPF ou CNPJ inválido") String cpfCnpj,
        @NotBlank(message = "Escolha a forma de pagamento")
        @Pattern(regexp = "CARD|PIX|BOLETO", message = "Forma de pagamento inválida") String paymentMethod) {}
