package br.com.sentinela.dto.response;

import java.util.List;

public record PropertyGeometryResponseDto(
        String type,
        List<List<List<Double>>> coordinates
) {
}
