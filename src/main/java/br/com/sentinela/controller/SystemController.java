package br.com.sentinela.controller;

import br.com.sentinela.service.MaintenanceService;
import br.com.sentinela.dto.response.SystemStatusDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final MaintenanceService maintenanceService;

    @GetMapping("/status")
    public ResponseEntity<SystemStatusDto> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(maintenanceService.getStatus());
    }
}
