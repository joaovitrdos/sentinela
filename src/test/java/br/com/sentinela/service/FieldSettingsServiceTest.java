package br.com.sentinela.service;

import br.com.sentinela.dto.request.FieldSettingsRequestDto;
import br.com.sentinela.entity.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FieldSettingsServiceTest {
    @Mock PropertyService properties;
    @InjectMocks FieldSettingsService service;

    @Test
    void administratorCanConfigureOperationalRules() {
        UUID userId = UUID.randomUUID(), propertyId = UUID.randomUUID();
        Property property = Property.builder().build();
        when(properties.requireAdmin(propertyId, userId)).thenReturn(property);

        var result = service.update(userId, propertyId,
                new FieldSettingsRequestDto(false, 3, true, "sacas"));

        assertThat(result.actionReminderEnabled()).isFalse();
        assertThat(result.actionReminderDays()).isEqualTo(3);
        assertThat(result.requireSeasonForFieldRecords()).isTrue();
        assertThat(result.defaultHarvestUnit()).isEqualTo("sacas");
    }

    @Test
    void viewerCannotChangeSettings() {
        UUID userId = UUID.randomUUID(), propertyId = UUID.randomUUID();
        when(properties.requireAdmin(propertyId, userId)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.update(userId, propertyId,
                new FieldSettingsRequestDto(true, 1, false, "kg")))
                .isInstanceOf(ResponseStatusException.class);
    }
}
