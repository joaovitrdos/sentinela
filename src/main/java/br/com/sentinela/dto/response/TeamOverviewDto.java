package br.com.sentinela.dto.response;

import br.com.sentinela.enums.TeamRole;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TeamOverviewDto(UUID propertyId, String propertyName, String title, boolean enabled, boolean owner,
                              TeamRole myRole, List<Member> members, List<Invitation> invitations) {
    public record Member(UUID userId, String name, String email, TeamRole role, LocalDateTime joinedAt, boolean owner) {}
    public record Invitation(UUID invitationId, String email, TeamRole role, LocalDateTime expiresAt) {}
}
