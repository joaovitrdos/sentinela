package br.com.sentinela.dto.response;

public record CarPropertyResponseDto(
        String code,
        String status,
        String condition,
        String type,
        String city,
        String state,
        Double areaHectares,
        PropertyGeometryResponseDto geometry,
        boolean simplified,
        int ignoredParts,
        String source) {}
