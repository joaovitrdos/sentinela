package br.com.sentinela.controller;

import br.com.sentinela.dto.request.PlotRequestDto;
import br.com.sentinela.dto.response.PlotResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.service.PlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/properties/{propertyId}/plots") @RequiredArgsConstructor
public class PlotController {
    private final PlotService plotService;

    @GetMapping
    public List<PlotResponseDto> list(@AuthenticationPrincipal User user, @PathVariable UUID propertyId) {
        return plotService.list(user.getUserId(), propertyId);
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public PlotResponseDto create(@AuthenticationPrincipal User user, @PathVariable UUID propertyId,
                                  @Valid @RequestBody PlotRequestDto request) {
        return plotService.create(user.getUserId(), propertyId, request);
    }

    @PutMapping("/{plotId}")
    public PlotResponseDto update(@AuthenticationPrincipal User user, @PathVariable UUID propertyId,
                                  @PathVariable UUID plotId, @Valid @RequestBody PlotRequestDto request) {
        return plotService.update(user.getUserId(), propertyId, plotId, request);
    }

    @DeleteMapping("/{plotId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable UUID propertyId, @PathVariable UUID plotId) {
        plotService.delete(user.getUserId(), propertyId, plotId);
    }
}
