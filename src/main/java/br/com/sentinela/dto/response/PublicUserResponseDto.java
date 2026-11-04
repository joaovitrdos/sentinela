package br.com.sentinela.dto.response;

import java.util.UUID;

public record PublicUserResponseDto(
        UUID userId,
        String name,
        String picture
) {
}
