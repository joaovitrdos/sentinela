package br.com.sentinela.service;

import br.com.sentinela.repository.SystemRepository;
import br.com.sentinela.dto.response.SystemStatusDto;
import br.com.sentinela.entity.System;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;

@Service
public class MaintenanceService {

    private final SystemRepository systemRepository;
    private final Clock clock;

    public MaintenanceService(SystemRepository systemRepository, @Qualifier("sentinelaClock") Clock clock) {
        this.systemRepository = systemRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SystemStatusDto getStatus() {
        var settings = systemRepository.findAll();
        var active = settings.stream()
                .filter(System::isMaintenance)
                .max(Comparator.comparing(System::getMaintenanceStart, Comparator.nullsLast(Comparator.naturalOrder())));
        System setting = active.orElse(settings.isEmpty() ? null : settings.getFirst());
        return new SystemStatusDto(active.isPresent(), setting == null ? null : setting.getVersion(),
                setting == null ? null : offset(setting.getMaintenanceStart()),
                setting == null ? null : offset(setting.getMaintenanceEnd()), clock.getZone().getId());
    }

    private OffsetDateTime offset(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(clock.getZone()).toOffsetDateTime();
    }
}
