package br.com.sentinela.mapper;

import br.com.sentinela.dto.response.PropertyGeometryResponseDto;
import br.com.sentinela.dto.response.PropertyResponseDto;
import br.com.sentinela.entity.Property;
import br.com.sentinela.enums.TeamRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PropertyMapper {

    @Mapping(target = "geometry", source = "geometry")
    PropertyResponseDto toResponse(Property property, PropertyGeometryResponseDto geometry, TeamRole accessRole, boolean teamOwner);

}
