package br.com.sentinela.service;

import br.com.sentinela.dto.response.PropertyGeometryResponseDto;
import br.com.sentinela.dto.request.PropertyRequestDto;
import br.com.sentinela.dto.response.PropertyResponseDto;
import br.com.sentinela.entity.Property;
import br.com.sentinela.mapper.PropertyMapper;
import br.com.sentinela.repository.PropertyRepository;
import br.com.sentinela.repository.PropertyActivityRepository;
import br.com.sentinela.repository.PlotRepository;
import br.com.sentinela.repository.FieldActionRepository;
import br.com.sentinela.repository.PropertyMemberRepository;
import br.com.sentinela.repository.PropertyInvitationRepository;
import br.com.sentinela.repository.UserRepository;
import br.com.sentinela.util.PropertyGeometry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final PropertyActivityRepository propertyActivityRepository;
    private final PlotRepository plotRepository;
    private final FieldActionRepository fieldActionRepository;
    private final PropertyMemberRepository memberRepository;
    private final PropertyInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final PropertyMapper propertyMapper;
    private final ObjectMapper objectMapper;
    private final TeamService teamService;

    @Transactional(readOnly = true)
    public List<PropertyResponseDto> findAll(UUID ownerId) {
        return propertyRepository.findAllAccessible(ownerId).stream()
                .map(property -> propertyMapper.toResponse(property, geometryOf(property), teamService.role(property, ownerId), isOwner(property, ownerId)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PropertyResponseDto findById(UUID ownerId, UUID propertyId) {
        Property property = teamService.requireView(propertyId, ownerId);
        return propertyMapper.toResponse(property, geometryOf(property), teamService.role(property, ownerId), isOwner(property, ownerId));
    }

    @Transactional
    public PropertyResponseDto create(UUID ownerId, PropertyRequestDto request) {
        Property property = Property.builder()
                .user(userRepository.getReferenceById(ownerId))
                .build();
        apply(property, request);
        Property saved = propertyRepository.saveAndFlush(property);
        return propertyMapper.toResponse(saved, geometryOf(saved), br.com.sentinela.enums.TeamRole.ADMIN, true);
    }

    @Transactional
    public PropertyResponseDto update(UUID ownerId, UUID propertyId, PropertyRequestDto request) {
        Property property = teamService.requireEdit(propertyId, ownerId);
        apply(property, request);
        Property saved = propertyRepository.saveAndFlush(property);
        return propertyMapper.toResponse(saved, geometryOf(saved), teamService.role(saved, ownerId), isOwner(saved, ownerId));
    }

    @Transactional
    public void delete(UUID ownerId, UUID propertyId) {
        Property property = teamService.requireAdmin(propertyId, ownerId);
        invitationRepository.deleteAllByProperty_PropertyId(propertyId);
        memberRepository.deleteAllByProperty_PropertyId(propertyId);
        fieldActionRepository.deleteAllByProperty_PropertyId(propertyId);
        propertyActivityRepository.deleteAllByProperty_PropertyId(propertyId);
        plotRepository.deleteAllByProperty_PropertyId(propertyId);
        propertyRepository.delete(property);
    }

    @Transactional(readOnly = true)
    public Property requireView(UUID propertyId, UUID userId) { return teamService.requireView(propertyId, userId); }

    @Transactional(readOnly = true)
    public Property requireEdit(UUID propertyId, UUID userId) { return teamService.requireEdit(propertyId, userId); }

    @Transactional(readOnly = true)
    public Property requireAdmin(UUID propertyId, UUID userId) { return teamService.requireAdmin(propertyId, userId); }

    public PropertyGeometryResponseDto geometryOf(Property property) {
        if (property.getPolygonJson() == null || property.getPolygonJson().isBlank()) {
            return null;
        }
        PropertyGeometryResponseDto geometry = objectMapper.readValue(property.getPolygonJson(), PropertyGeometryResponseDto.class);
        return PropertyGeometry.validate(geometry).geometry();
    }

    private void apply(Property property, PropertyRequestDto request) {
        PropertyGeometry.Validated area = PropertyGeometry.validate(request.geometry());
        property.setName(request.name().trim());
        property.setCity(request.city().trim());
        property.setState(request.state().trim());
        property.setCropName(clean(request.cropName()));
        property.setPlotName(clean(request.plotName()));
        property.setPlantingDate(request.plantingDate());
        property.setCropStage(clean(request.cropStage()));
        property.setLatitude(area.latitude());
        property.setLongitude(area.longitude());
        property.setAreaHectares(area.areaHectares());
        property.setPolygonJson(objectMapper.writeValueAsString(area.geometry()));
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isOwner(Property property, UUID userId) {
        return property.getUser().getUserId().equals(userId);
    }

}
