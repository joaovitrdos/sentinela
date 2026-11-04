package br.com.sentinela.dto.request;

import br.com.sentinela.enums.TeamRole;
import jakarta.validation.constraints.NotNull;

public record TeamRoleRequestDto(@NotNull TeamRole role) {}
