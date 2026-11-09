package br.com.sentinela.service;

import br.com.sentinela.util.Util;

import br.com.sentinela.repository.SystemRepository;
import br.com.sentinela.dto.response.SystemStatusDto;
import br.com.sentinela.entity.System;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;

@Service
public class MaintenanceService {

    private final SystemRepository systemRepository;
    private final Clock clock;
    private final Cache<Boolean, SystemStatusDto> statusCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(1))
            .maximumSize(1)
            .build();

    public MaintenanceService(SystemRepository systemRepository, @Qualifier("sentinelaClock") Clock clock) {
        this.systemRepository = systemRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SystemStatusDto getStatus() {
        return statusCache.get(true, ignored -> loadStatus());
    }

    private SystemStatusDto loadStatus() {
        var settings = systemRepository.findAll();
        var active = settings.stream()
                .filter(System::isMaintenance)
                .max(Comparator.comparing(System::getMaintenanceStart, Comparator.nullsLast(Comparator.naturalOrder())));
        System setting = active.orElse(Util.isNullOrEmpty(settings) ? null : settings.getFirst());
        return new SystemStatusDto(active.isPresent(), Util.isNull(setting) ? null : setting.getVersion(),
                Util.isNull(setting) ? null : offset(setting.getMaintenanceStart()),
                Util.isNull(setting) ? null : offset(setting.getMaintenanceEnd()), clock.getZone().getId());
    }

    private OffsetDateTime offset(LocalDateTime dateTime) {
        return Util.isNull(dateTime) ? null : dateTime.atZone(clock.getZone()).toOffsetDateTime();
    }
}
