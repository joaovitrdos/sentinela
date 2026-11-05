package br.com.sentinela.controller;

import br.com.sentinela.enums.Feature;
import br.com.sentinela.infra.security.RequiresFeature;
import br.com.sentinela.dto.response.HydroStationResponseDto;
import br.com.sentinela.dto.response.RainReadingResponseDto;
import br.com.sentinela.dto.response.RiverLevelResponseDto;
import br.com.sentinela.dto.response.RiverNetworkResponseDto;
import br.com.sentinela.service.RainService;
import br.com.sentinela.service.ReferenceLevelService;
import br.com.sentinela.service.RiverService;
import br.com.sentinela.service.StationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/hydro")
@RequiredArgsConstructor
@RequiresFeature(Feature.BASE)
public class HydroController {

    private final RainService rainService;
    private final ReferenceLevelService referenceLevelService;
    private final StationService stationService;
    private final RiverService riverService;

    @GetMapping("/rain/24h")
    public List<RainReadingResponseDto> getRainForLast24Hours() {
        return rainService.getRain24h();
    }

    @GetMapping("/levels")
    public List<RiverLevelResponseDto> getRiverLevels() {
        return referenceLevelService.getRiverLevels();
    }

    @GetMapping("/stations")
    public List<HydroStationResponseDto> getHydroStations() {
        return stationService.getStations();
    }

    @GetMapping("/rivers")
    public ResponseEntity<RiverNetworkResponseDto> getRiverNetwork() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(riverService.getRivers());
    }
}
