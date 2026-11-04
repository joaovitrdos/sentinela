package br.com.sentinela.dto.response;

public record AuthResponseDto(
        String accessToken,
        String tokenType,
        UserResponseDto user
) {

    private static final String BEARER = "Bearer";

    public static AuthResponseDto bearer(String accessToken, UserResponseDto user) {
        return new AuthResponseDto(accessToken, BEARER, user);
    }
}
