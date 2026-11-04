package br.com.sentinela.dto.response;

import java.time.OffsetDateTime;

public record SystemStatusDto(
        boolean maintenance,
        String version,
        OffsetDateTime maintenanceStart,
        OffsetDateTime maintenanceEnd,
        String timeZone) {
}
