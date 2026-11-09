package br.com.sentinela.controller;

import br.com.sentinela.dto.response.PlantingWindowResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.enums.Feature;
import br.com.sentinela.infra.security.RequiresFeature;
import br.com.sentinela.service.PlantingWindowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/properties/{propertyId}")
@RequiredArgsConstructor
@RequiresFeature(Feature.PLANTING_WINDOW)
public class PlantingWindowController {

    private final PlantingWindowService plantingWindowService;

    @GetMapping("/planting-window")
    public ResponseEntity<PlantingWindowResponseDto> plantingWindow(@AuthenticationPrincipal User user, @PathVariable UUID propertyId,
                                                                    @RequestParam(required = false) String crop) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(plantingWindowService.window(propertyId, user.getUserId(), crop));
    }
}
