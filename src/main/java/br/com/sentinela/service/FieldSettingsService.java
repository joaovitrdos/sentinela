package br.com.sentinela.service;

import br.com.sentinela.dto.request.FieldSettingsRequestDto;
import br.com.sentinela.dto.response.FieldSettingsResponseDto;
import br.com.sentinela.entity.Property;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class FieldSettingsService {
    private final PropertyService properties;

    @Transactional(readOnly = true)
    public FieldSettingsResponseDto get(UUID userId, UUID propertyId) {
        return response(properties.requireView(propertyId, userId));
    }

    @Transactional
    public FieldSettingsResponseDto update(UUID userId, UUID propertyId, FieldSettingsRequestDto request) {
        Property property = properties.requireAdmin(propertyId, userId);
        property.setActionReminderEnabled(request.actionReminderEnabled());
        property.setActionReminderDays(request.actionReminderDays());
        property.setRequireSeasonForFieldRecords(request.requireSeasonForFieldRecords());
        property.setDefaultHarvestUnit(request.defaultHarvestUnit());
        return response(property);
    }

    private FieldSettingsResponseDto response(Property property) {
        return new FieldSettingsResponseDto(Boolean.TRUE.equals(property.getActionReminderEnabled()),
                property.getActionReminderDays(), Boolean.TRUE.equals(property.getRequireSeasonForFieldRecords()),
                property.getDefaultHarvestUnit());
    }
}
