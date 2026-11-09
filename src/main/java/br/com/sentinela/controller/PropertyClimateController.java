package br.com.sentinela.controller;

import br.com.sentinela.dto.response.ClimateHistoryResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.enums.Feature;
import br.com.sentinela.infra.security.RequiresFeature;
import br.com.sentinela.service.ClimateHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/properties/{propertyId}")
@RequiredArgsConstructor
@RequiresFeature(Feature.BASE)
public class PropertyClimateController {

    private final ClimateHistoryService climateHistoryService;

    @GetMapping("/climate-history")
    public ResponseEntity<ClimateHistoryResponseDto> climateHistory(@AuthenticationPrincipal User user, @PathVariable UUID propertyId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(climateHistoryService.history(propertyId, user.getUserId()));
    }
}
