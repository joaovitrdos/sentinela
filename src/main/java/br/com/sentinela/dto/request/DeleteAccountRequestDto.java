package br.com.sentinela.dto.request;

public record DeleteAccountRequestDto(
        String currentPassword,
        String googleIdToken
) {
}
