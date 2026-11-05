package br.com.sentinela.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code invoiceUrl} leva à fatura do Asaas, onde o cliente escolhe a forma de pagamento e digita o cartão. */
public record SubscribeResponseDto(String planCode, String cycle, BigDecimal value, LocalDate nextDueDate, String invoiceUrl) {}
