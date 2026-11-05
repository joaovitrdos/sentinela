package br.com.sentinela.controller;

import br.com.sentinela.enums.Feature;
import br.com.sentinela.infra.security.RequiresFeature;
import br.com.sentinela.dto.response.PropertyMonitoringDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.service.CopernicusService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/properties/{propertyId}")
@RequiredArgsConstructor
@RequiresFeature(Feature.NDVI)
public class PropertyMonitoringController {

    private final CopernicusService copernicusService;

    @GetMapping("/monitoring")
    public ResponseEntity<PropertyMonitoringDto> monitoring(
            @AuthenticationPrincipal User user,
            @PathVariable UUID propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "40") int maxCloudCoverage) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(copernicusService.monitor(propertyId, user.getUserId(), from, to, maxCloudCoverage));
    }

    @GetMapping(value = "/ndvi.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> ndviImage(
            @AuthenticationPrincipal User user,
            @PathVariable UUID propertyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "40") int maxCloudCoverage) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .contentType(MediaType.IMAGE_PNG)
                .body(copernicusService.ndviImage(propertyId, user.getUserId(), date, maxCloudCoverage));
    }
}
