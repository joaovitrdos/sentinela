package br.com.sentinela.dto.response;

import br.com.sentinela.enums.AuthProvider;

import java.util.UUID;

public record UserResponseDto(
        UUID userId,
        String name,
        String email,
        String picture,
        AuthProvider authProvider,
        boolean emailVerified
) {
}
