package br.com.sentinela.controller;

import br.com.sentinela.dto.request.PlotSeasonRequestDto;
import br.com.sentinela.dto.response.PlotSeasonResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.service.PlotSeasonService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/properties/{propertyId}")
public class PlotSeasonController {
    private final PlotSeasonService service;
    @GetMapping("/seasons")
    public List<PlotSeasonResponseDto> list(@AuthenticationPrincipal User user, @PathVariable UUID propertyId) {
        return service.list(user.getUserId(), propertyId);
    }
    @PostMapping("/plots/{plotId}/seasons") @ResponseStatus(HttpStatus.CREATED)
    public PlotSeasonResponseDto create(@AuthenticationPrincipal User user, @PathVariable UUID propertyId,
            @PathVariable UUID plotId, @Valid @RequestBody PlotSeasonRequestDto request) {
        return service.create(user.getUserId(), propertyId, plotId, request);
    }
    @PostMapping("/plots/{plotId}/seasons/{seasonId}/close")
    public PlotSeasonResponseDto close(@AuthenticationPrincipal User user, @PathVariable UUID propertyId,
            @PathVariable UUID plotId, @PathVariable UUID seasonId, @Valid @RequestBody CloseSeasonRequest request) {
        return service.close(user.getUserId(), propertyId, plotId, seasonId, request.harvestDate());
    }
    public record CloseSeasonRequest(@NotNull LocalDate harvestDate) {}
}
