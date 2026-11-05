package br.com.sentinela.controller;

import br.com.sentinela.enums.Feature;
import br.com.sentinela.infra.security.RequiresFeature;
import br.com.sentinela.dto.request.PropertyAlertSettingsDto;
import br.com.sentinela.dto.response.PropertyAlertResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.service.PropertyAlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RequiresFeature(Feature.ALERTS)
@RestController @RequiredArgsConstructor
@RequestMapping("/api/properties/{propertyId}/alerts")
public class PropertyAlertController {
    private final PropertyAlertService service;

    @GetMapping
    public List<PropertyAlertResponseDto> list(@AuthenticationPrincipal User user, @PathVariable UUID propertyId) {
        return service.list(user.getUserId(), propertyId);
    }

    @GetMapping("/settings")
    public PropertyAlertSettingsDto settings(@AuthenticationPrincipal User user, @PathVariable UUID propertyId) {
        return service.settings(user.getUserId(), propertyId);
    }

    @PutMapping("/settings")
    public PropertyAlertSettingsDto update(@AuthenticationPrincipal User user, @PathVariable UUID propertyId,
                                           @Valid @RequestBody PropertyAlertSettingsDto request) {
        return service.updateSettings(user.getUserId(), propertyId, request);
    }
}
