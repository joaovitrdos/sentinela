package br.com.sentinela.service;

import br.com.sentinela.entity.System;
import br.com.sentinela.repository.SystemRepository;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaintenanceServiceTest {
    private final SystemRepository repository = mock(SystemRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private final MaintenanceService service = new MaintenanceService(repository, clock);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 2, 9, 0);

    @Test
    void missingSettingsLeaveTheSystemAvailable() {
        when(repository.findAll()).thenReturn(List.of());
        assertThat(service.getStatus().maintenance()).isFalse();
    }

    @Test
    void enabledMaintenanceWithoutDatesStartsImmediately() {
        when(repository.findAll()).thenReturn(List.of(System.builder().version("1.2.0").maintenance(true).build()));
        assertThat(service.getStatus().maintenance()).isTrue();
        assertThat(service.getStatus().version()).isEqualTo("1.2.0");
        assertThat(service.getStatus().maintenanceEnd()).isNull();
    }

    @Test
    void disabledMaintenanceDoesNotBlockAccess() {
        when(repository.findAll()).thenReturn(List.of(System.builder().maintenance(false).maintenanceStart(now.minusHours(1)).build()));
        assertThat(service.getStatus().maintenance()).isFalse();
    }

    @Test
    void theEntityFlagControlsMaintenanceEvenWhenDatesAreMissingOrInconsistent() {
        when(repository.findAll()).thenReturn(List.of(System.builder().maintenance(true).maintenanceStart(now.plusMinutes(1)).build()));
        assertThat(service.getStatus().maintenance()).isTrue();
    }

    @Test
    void aCompletedMaintenanceKeepsItsAuditDatesWithoutBlockingAccess() {
        when(repository.findAll()).thenReturn(List.of(System.builder().maintenance(false).maintenanceStart(now.minusHours(1)).maintenanceEnd(now).build()));
        assertThat(service.getStatus().maintenance()).isFalse();
        assertThat(service.getStatus().maintenanceEnd()).isNotNull();
    }

    @Test
    void activeWindowsIncludeOffsetsFromTheConfiguredTimeZone() {
        when(repository.findAll()).thenReturn(List.of(System.builder().maintenance(true).maintenanceStart(now).maintenanceEnd(now.plusHours(1)).build()));
        var status = service.getStatus();
        assertThat(status.maintenance()).isTrue();
        assertThat(status.maintenanceStart().toString()).isEqualTo("2026-11-02T09:00-03:00");
        assertThat(status.maintenanceEnd().toString()).isEqualTo("2026-11-02T10:00-03:00");
        assertThat(status.timeZone()).isEqualTo("America/Sao_Paulo");
    }

    @Test
    void anyEnabledSystemRecordKeepsMaintenanceActive() {
        when(repository.findAll()).thenReturn(List.of(
                System.builder().maintenance(false).version("disabled").build(),
                System.builder().maintenance(true).version("indefinite").build()));
        assertThat(service.getStatus().maintenanceEnd()).isNull();
        assertThat(service.getStatus().version()).isEqualTo("indefinite");
    }
}
