package br.com.sentinela.service;

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
        return response(plotRepository.saveAndFlush(plot));
    }

    @Transactional
    public PlotResponseDto update(UUID ownerId, UUID propertyId, UUID plotId, PlotRequestDto request) {
        propertyService.requireEdit(propertyId, ownerId);
        Plot plot = requirePlot(propertyId, ownerId, plotId);
        if (!plot.getName().equalsIgnoreCase(request.name().trim()) &&
                plotRepository.existsByProperty_PropertyIdAndNameIgnoreCase(propertyId, request.name().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um talhão com esse nome");
        }
        apply(plot, request);
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

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private PlotResponseDto response(Plot plot) {
        return new PlotResponseDto(plot.getPlotId(), plot.getName(), plot.getCropName(),
                plot.getPlantingDate(), plot.getCropStage(), plot.getAreaHectares());
    }
}
