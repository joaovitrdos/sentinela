package br.com.sentinela.service;

import br.com.sentinela.infra.config.SnirhHydroProperties;
import br.com.sentinela.util.LogUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HydroPreloader {

    private final SnirhHydroProperties properties;
    private final RainService rainService;
    private final ReferenceLevelService referenceLevelService;
    private final StationService stationService;
    private final RiverService riverService;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void preload() {
        if (!properties.preload()) {
            return;
        }
        load("24-hour rainfall", rainService::getRain24h);
        load("river levels", referenceLevelService::getRiverLevels);
        load("hydro stations", stationService::getStations);
        load("rivers", riverService::getRivers);
    }

    private static void load(String name, Runnable loader) {
        try {
            loader.run();
        } catch (RuntimeException exception) {
            LogUtil.warn("SNIRH preload for {0} failed: {1}", name, exception.getMessage());
        }
    }
}
