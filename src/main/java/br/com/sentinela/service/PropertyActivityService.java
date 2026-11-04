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
        var activity = PropertyActivity.builder()
                .property(property)
                .plot(request.plotId() == null ? null : plotService.requirePlot(propertyId, ownerId, request.plotId()))
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
                activity.getPlot() == null ? null : activity.getPlot().getPlotId());
    }
}
