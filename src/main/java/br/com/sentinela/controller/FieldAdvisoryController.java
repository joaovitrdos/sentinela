package br.com.sentinela.controller;

import br.com.sentinela.dto.response.IrrigationRecommendationResponseDto;
import br.com.sentinela.dto.response.SprayWindowResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.enums.Feature;
import br.com.sentinela.infra.security.RequiresFeature;
import br.com.sentinela.service.IrrigationService;
import br.com.sentinela.service.SprayWindowService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/properties/{propertyId}")
public class FieldAdvisoryController {
    private final SprayWindowService sprayWindows;
    private final IrrigationService irrigation;

    @RequiresFeature(Feature.SPRAY_WINDOW)
    @GetMapping("/spray-windows")
    public SprayWindowResponseDto sprayWindows(@AuthenticationPrincipal User user, @PathVariable UUID propertyId) {
        return sprayWindows.windows(user.getUserId(), propertyId);
    }

    @RequiresFeature(Feature.IRRIGATION)
    @GetMapping("/plots/{plotId}/irrigation-recommendation")
    public IrrigationRecommendationResponseDto irrigation(@AuthenticationPrincipal User user, @PathVariable UUID propertyId,
                                                          @PathVariable UUID plotId) {
        return irrigation.recommend(user.getUserId(), propertyId, plotId);
    }
}
