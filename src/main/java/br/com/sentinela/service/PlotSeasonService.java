package br.com.sentinela.service;

import br.com.sentinela.util.Util;
import br.com.sentinela.dto.request.PlotSeasonRequestDto;
import br.com.sentinela.dto.response.PlotSeasonResponseDto;
import br.com.sentinela.entity.PlotSeason;
import br.com.sentinela.repository.PlotSeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class PlotSeasonService {
    private final PropertyService properties;
    private final PlotService plots;
    private final PlotSeasonRepository seasons;
    private final br.com.sentinela.repository.PropertyActivityRepository activities;

    @Transactional(readOnly = true)
    public List<PlotSeasonResponseDto> list(UUID userId, UUID propertyId) {
        properties.requireView(propertyId, userId);
        return seasons.findAllByPlot_Property_PropertyIdOrderByPlantingDateDesc(propertyId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public PlotSeason requireSeason(UUID userId, UUID propertyId, UUID plotId, UUID seasonId) {
        plots.requirePlot(propertyId, userId, plotId);
        return seasons.findBySeasonIdAndPlot_PlotIdAndPlot_Property_PropertyId(seasonId, plotId, propertyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Safra não encontrada"));
    }

    @Transactional
    public PlotSeasonResponseDto create(UUID userId, UUID propertyId, UUID plotId, PlotSeasonRequestDto request) {
        properties.requireEdit(propertyId, userId);
        var plot = plots.requirePlot(propertyId, userId, plotId);
        if (seasons.existsByPlot_PlotIdAndStatus(plotId, "ACTIVE")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Encerre a safra ativa antes de iniciar outra");
        }
        if (seasons.findAllByPlot_PlotIdOrderByPlantingDateDesc(plotId).stream().anyMatch(previous ->
                Util.isNull(previous.getHarvestDate()) || !request.plantingDate().isAfter(previous.getHarvestDate())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O plantio deve ocorrer depois da última colheita");
        var season = PlotSeason.builder().plot(plot).cropName(request.cropName().trim())
                .variety(Util.trimToNull(request.variety()))
                .plantingDate(request.plantingDate()).status("ACTIVE").build();
        plot.setCropName(season.getCropName());
        plot.setPlantingDate(season.getPlantingDate());
        return response(seasons.saveAndFlush(season));
    }

    @Transactional
    public PlotSeasonResponseDto close(UUID userId, UUID propertyId, UUID plotId, UUID seasonId, LocalDate harvestDate) {
        properties.requireEdit(propertyId, userId);
        var season = requireSeason(userId, propertyId, plotId, seasonId);
        if (!"ACTIVE".equals(season.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Safra já encerrada");
        if (Util.isNull(harvestDate) || harvestDate.isBefore(season.getPlantingDate()) || harvestDate.isAfter(LocalDate.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data de colheita inválida");
        if (activities.existsBySeason_SeasonIdAndDateAfter(seasonId, harvestDate))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Há operações posteriores à data de encerramento");
        season.setHarvestDate(harvestDate);
        season.setStatus("CLOSED");
        season.getPlot().setCropName(null);
        season.getPlot().setPlantingDate(null);
        season.getPlot().setCropStage(null);
        return response(seasons.saveAndFlush(season));
    }

    private PlotSeasonResponseDto response(PlotSeason season) {
        return new PlotSeasonResponseDto(season.getSeasonId(), season.getPlot().getPlotId(), season.getCropName(),
                season.getVariety(), season.getPlantingDate(), season.getHarvestDate(), season.getStatus());
    }
}
