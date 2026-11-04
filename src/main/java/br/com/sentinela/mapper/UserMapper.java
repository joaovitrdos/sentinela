package br.com.sentinela.mapper;

import br.com.sentinela.dto.response.PublicUserResponseDto;
import br.com.sentinela.dto.response.UserResponseDto;
import br.com.sentinela.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

    UserResponseDto toResponse(User user);

    PublicUserResponseDto toPublicResponse(User user);

}
