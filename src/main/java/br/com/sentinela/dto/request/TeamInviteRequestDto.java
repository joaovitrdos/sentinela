package br.com.sentinela.dto.request;

import br.com.sentinela.enums.TeamRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TeamInviteRequestDto(@NotBlank @Email @Size(max = 320) String email, @NotNull TeamRole role) {}
