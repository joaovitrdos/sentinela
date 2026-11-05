package br.com.sentinela.service;

import br.com.sentinela.dto.request.PropertyActivityRequestDto;
import br.com.sentinela.dto.response.PropertyActivityResponseDto;
import br.com.sentinela.entity.PropertyActivity;
import br.com.sentinela.repository.PropertyActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PropertyActivityService {
    private final PropertyService propertyService;
    private final PropertyActivityRepository activityRepository;
    private final PlotService plotService;
    private final PlotSeasonService seasonService;
    private final TeamService teamService;

    @Transactional(readOnly = true)
    public List<PropertyActivityResponseDto> list(UUID ownerId, UUID propertyId) {
        propertyService.requireView(propertyId, ownerId);
        return activityRepository.findAllByProperty_PropertyIdOrderByDateDescCreatedAtDesc(propertyId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public PropertyActivityResponseDto create(UUID ownerId, UUID propertyId, PropertyActivityRequestDto request) {
        if ("RAINFALL".equals(request.type()) && request.amountMm() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a chuva medida em milímetros");
        }
        var property = propertyService.requireEdit(propertyId, ownerId);
        if (Boolean.TRUE.equals(property.getRequireSeasonForFieldRecords()) &&
                java.util.Set.of("PLANTING", "FERTILIZATION", "SPRAYING", "HARVEST").contains(request.type()) &&
                request.seasonId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma safra para esta operação");
        var plot = request.plotId() == null ? null : plotService.requirePlot(propertyId, ownerId, request.plotId());
        if (request.seasonId() != null && plot == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o talhão da safra");
        var season = request.seasonId() == null ? null : seasonService.requireSeason(ownerId, propertyId, request.plotId(), request.seasonId());
        if (season != null && (request.date().isBefore(season.getPlantingDate()) ||
                season.getHarvestDate() != null && request.date().isAfter(season.getHarvestDate())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data fora do período da safra");
        if ((request.inputName() == null) != (request.inputQuantity() == null) ||
                (request.inputName() != null && (request.inputName().isBlank() || request.inputUnit() == null || request.inputUnit().isBlank())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe insumo, quantidade e unidade juntos");
        if ((request.harvestQuantity() == null) != (request.harvestUnit() == null) ||
                (request.harvestQuantity() != null && (!"HARVEST".equals(request.type()) || request.harvestUnit().isBlank())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantidade colhida requer operação de colheita e unidade");
        var responsible = request.responsibleUserId() == null ? null : teamService.requireActiveMember(property, request.responsibleUserId());
        var activity = PropertyActivity.builder()
                .property(property)
                .plot(plot).season(season).responsibleUser(responsible)
                .responsibleName(responsible == null ? null : responsible.getName())
                .inputName(request.inputName() == null ? null : request.inputName().trim())
                .inputQuantity(request.inputQuantity()).inputUnit(request.inputUnit() == null ? null : request.inputUnit().trim())
                .harvestQuantity(request.harvestQuantity()).harvestUnit(request.harvestUnit() == null ? null : request.harvestUnit().trim())
                .type(request.type())
                .date(request.date())
                .time(request.time())
                .notes(request.notes().trim())
                .amountMm(request.amountMm())
                .build();
        return toResponse(activityRepository.save(activity));
    }

    @Transactional
    public void delete(UUID ownerId, UUID propertyId, UUID activityId) {
        propertyService.requireAdmin(propertyId, ownerId);
        PropertyActivity activity = activityRepository.findById(activityId)
                .filter(row -> row.getProperty().getPropertyId().equals(propertyId))
                .orElseThrow(br.com.sentinela.exception.PropertyNotFoundException::new);
        activityRepository.delete(activity);
    }

    private PropertyActivityResponseDto toResponse(PropertyActivity activity) {
        return new PropertyActivityResponseDto(activity.getActivityId(), activity.getType(), activity.getDate(), activity.getTime(),
                activity.getNotes(), activity.getAmountMm(), activity.getCreatedAt(),
                activity.getPlot() == null ? null : activity.getPlot().getPlotId(),
                activity.getSeason() == null ? null : activity.getSeason().getSeasonId(),
                activity.getResponsibleUser() == null ? null : activity.getResponsibleUser().getUserId(),
                activity.getResponsibleName(), activity.getInputName(), activity.getInputQuantity(), activity.getInputUnit(),
                activity.getHarvestQuantity(), activity.getHarvestUnit());
    }
}
