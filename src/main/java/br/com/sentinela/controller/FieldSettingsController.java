package br.com.sentinela.controller;

import br.com.sentinela.dto.request.FieldSettingsRequestDto;
import br.com.sentinela.dto.response.FieldSettingsResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.service.FieldSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/properties/{propertyId}/field-settings")
public class FieldSettingsController {
    private final FieldSettingsService service;

    @GetMapping
    public FieldSettingsResponseDto get(@AuthenticationPrincipal User user, @PathVariable UUID propertyId) {
        return service.get(user.getUserId(), propertyId);
    }

    @PutMapping
    public FieldSettingsResponseDto update(@AuthenticationPrincipal User user, @PathVariable UUID propertyId,
                                           @Valid @RequestBody FieldSettingsRequestDto request) {
        return service.update(user.getUserId(), propertyId, request);
    }
}
