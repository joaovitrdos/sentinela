package br.com.sentinela.controller;

import br.com.sentinela.dto.request.PropertyActivityRequestDto;
import br.com.sentinela.dto.response.PropertyActivityResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.service.PropertyActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/properties/{propertyId}/activities")
@RequiredArgsConstructor
public class PropertyActivityController {
    private final PropertyActivityService activityService;

    @GetMapping
    public List<PropertyActivityResponseDto> list(@AuthenticationPrincipal User user, @PathVariable UUID propertyId) {
        return activityService.list(user.getUserId(), propertyId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyActivityResponseDto create(@AuthenticationPrincipal User user, @PathVariable UUID propertyId,
            @RequestBody @Valid PropertyActivityRequestDto request) {
        return activityService.create(user.getUserId(), propertyId, request);
    }

    @DeleteMapping("/{activityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable UUID propertyId, @PathVariable UUID activityId) {
        activityService.delete(user.getUserId(), propertyId, activityId);
    }
}
