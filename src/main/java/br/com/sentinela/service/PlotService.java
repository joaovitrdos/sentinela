package br.com.sentinela.service;

import br.com.sentinela.util.Util;
import br.com.sentinela.dto.request.PlotRequestDto;
import br.com.sentinela.dto.response.PlotResponseDto;
import br.com.sentinela.entity.Plot;
import br.com.sentinela.repository.PlotRepository;
import br.com.sentinela.repository.PropertyActivityRepository;
import br.com.sentinela.repository.FieldActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class PlotService {
    private final PropertyService propertyService;
    private final PlotRepository plotRepository;
    private final PropertyActivityRepository activityRepository;
    private final FieldActionRepository actionRepository;
    private final br.com.sentinela.repository.PlotSeasonRepository seasonRepository;

    @Transactional(readOnly = true)
    public List<PlotResponseDto> list(UUID ownerId, UUID propertyId) {
        propertyService.requireView(propertyId, ownerId);
        return plotRepository.findAllByProperty_PropertyIdOrderByName(propertyId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public Plot requirePlot(UUID propertyId, UUID ownerId, UUID plotId) {
        propertyService.requireView(propertyId, ownerId);
        return plotRepository.findByPlotIdAndProperty_PropertyId(plotId, propertyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Talhão não encontrado"));
    }

    @Transactional
    public PlotResponseDto create(UUID ownerId, UUID propertyId, PlotRequestDto request) {
        var property = propertyService.requireEdit(propertyId, ownerId);
        String name = request.name().trim();
        if (plotRepository.existsByProperty_PropertyIdAndNameIgnoreCase(propertyId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um talhão com esse nome");
        }
        Plot plot = Plot.builder().property(property).build();
        apply(plot, request);
        plotRepository.saveAndFlush(plot);
        if (Util.nonNull(plot.getCropName()) && Util.nonNull(plot.getPlantingDate())) {
            seasonRepository.saveAndFlush(br.com.sentinela.entity.PlotSeason.builder().plot(plot)
                    .cropName(plot.getCropName()).plantingDate(plot.getPlantingDate()).status("ACTIVE").build());
        }
        return response(plot);
    }

    @Transactional
    public PlotResponseDto update(UUID ownerId, UUID propertyId, UUID plotId, PlotRequestDto request) {
        propertyService.requireEdit(propertyId, ownerId);
        Plot plot = requirePlot(propertyId, ownerId, plotId);
        if (seasonRepository.existsByPlot_PlotIdAndStatus(plotId, "ACTIVE") &&
                (!java.util.Objects.equals(plot.getCropName(), clean(request.cropName())) ||
                 !java.util.Objects.equals(plot.getPlantingDate(), request.plantingDate())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Encerre a safra ativa antes de alterar cultura ou plantio");
        if (!plot.getName().equalsIgnoreCase(request.name().trim()) &&
                plotRepository.existsByProperty_PropertyIdAndNameIgnoreCase(propertyId, request.name().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um talhão com esse nome");
        }
        apply(plot, request);
        if (!seasonRepository.existsByPlot_PlotIdAndStatus(plotId, "ACTIVE") && Util.nonNull(plot.getCropName()) && Util.nonNull(plot.getPlantingDate())) {
            if (seasonRepository.findAllByPlot_PlotIdOrderByPlantingDateDesc(plotId).stream().anyMatch(previous ->
                    Util.isNull(previous.getHarvestDate()) || !plot.getPlantingDate().isAfter(previous.getHarvestDate())))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "O novo plantio deve ocorrer depois da última colheita");
            seasonRepository.save(br.com.sentinela.entity.PlotSeason.builder().plot(plot).cropName(plot.getCropName())
                    .plantingDate(plot.getPlantingDate()).status("ACTIVE").build());
        }
        return response(plotRepository.saveAndFlush(plot));
    }

    @Transactional
    public void delete(UUID ownerId, UUID propertyId, UUID plotId) {
        propertyService.requireAdmin(propertyId, ownerId);
        Plot plot = requirePlot(propertyId, ownerId, plotId);
        activityRepository.detachPlot(plotId);
        actionRepository.detachPlot(plotId);
        plotRepository.delete(plot);
        plotRepository.flush();
    }

    private void apply(Plot plot, PlotRequestDto request) {
        plot.setName(request.name().trim());
        plot.setCropName(clean(request.cropName()));
        plot.setPlantingDate(request.plantingDate());
        plot.setCropStage(clean(request.cropStage()));
        plot.setAreaHectares(request.areaHectares());
    }

    private String clean(String value) { return Util.trimToNull(value); }
    private PlotResponseDto response(Plot plot) {
        return new PlotResponseDto(plot.getPlotId(), plot.getName(), plot.getCropName(),
                plot.getPlantingDate(), plot.getCropStage(), plot.getAreaHectares());
    }
}
