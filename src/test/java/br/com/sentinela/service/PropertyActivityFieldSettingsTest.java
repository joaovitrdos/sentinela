package br.com.sentinela.service;

import br.com.sentinela.dto.request.PropertyActivityRequestDto;
import br.com.sentinela.entity.Property;
import br.com.sentinela.repository.PropertyActivityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyActivityFieldSettingsTest {
    @Mock PropertyService properties;
    @Mock PropertyActivityRepository activities;
    @Mock PlotService plots;
    @Mock PlotSeasonService seasons;
    @Mock TeamService teams;
    @InjectMocks PropertyActivityService service;

    @Test
    void configuredPropertyRejectsFieldOperationWithoutSeason() {
        UUID userId = UUID.randomUUID(), propertyId = UUID.randomUUID();
        when(properties.requireEdit(propertyId, userId)).thenReturn(Property.builder().requireSeasonForFieldRecords(true).build());
        var request = new PropertyActivityRequestDto("HARVEST", LocalDate.now(), null, "Colheita final", null, UUID.randomUUID());

        assertThatThrownBy(() -> service.create(userId, propertyId, request))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(activities);
    }
}
