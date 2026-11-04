package br.com.sentinela.controller;

import br.com.sentinela.dto.request.PropertyRequestDto;
import br.com.sentinela.dto.response.PropertyResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.service.PropertyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    @GetMapping
    public List<PropertyResponseDto> findAll(@AuthenticationPrincipal User principal) {
        return propertyService.findAll(principal.getUserId());
    }

    @GetMapping("/{propertyId}")
    public PropertyResponseDto findById(
            @AuthenticationPrincipal User principal,
            @PathVariable UUID propertyId) {
        return propertyService.findById(principal.getUserId(), propertyId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyResponseDto create(
            @AuthenticationPrincipal User principal,
            @RequestBody @Valid PropertyRequestDto request) {
        return propertyService.create(principal.getUserId(), request);
    }

    @PutMapping("/{propertyId}")
    public PropertyResponseDto update(
            @AuthenticationPrincipal User principal,
            @PathVariable UUID propertyId,
            @RequestBody @Valid PropertyRequestDto request) {
        return propertyService.update(principal.getUserId(), propertyId, request);
    }

    @DeleteMapping("/{propertyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal User principal,
            @PathVariable UUID propertyId) {
        propertyService.delete(principal.getUserId(), propertyId);
    }
}
