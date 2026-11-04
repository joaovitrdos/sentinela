package br.com.sentinela.dto.response;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.UUID;
import br.com.sentinela.enums.TeamRole;

public record PropertyResponseDto(
        UUID propertyId,
        String name,
        String teamTitle,
        String city,
        String state,
        Double latitude,
        Double longitude,
        Double areaHectares,
        PropertyGeometryResponseDto geometry,
        String cropName,
        String plotName,
        LocalDate plantingDate,
        String cropStage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        TeamRole accessRole,
        boolean teamOwner
) {
}
