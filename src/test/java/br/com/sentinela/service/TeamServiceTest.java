package br.com.sentinela.service;

import br.com.sentinela.entity.Property;
import br.com.sentinela.entity.PropertyMember;
import br.com.sentinela.entity.PropertyInvitation;
import br.com.sentinela.entity.User;
import br.com.sentinela.enums.TeamRole;
import br.com.sentinela.repository.PropertyInvitationRepository;
import br.com.sentinela.repository.PropertyMemberRepository;
import br.com.sentinela.repository.PropertyRepository;
import br.com.sentinela.repository.UserRepository;
import br.com.sentinela.notification.EmailFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {
    @Mock PropertyRepository properties;
    @Mock PropertyMemberRepository members;
    @Mock PropertyInvitationRepository invitations;
    @Mock UserRepository users;
    @Mock EmailFactory emailFactory;
    @Mock ApplicationEventPublisher events;
    @Mock PlanService plans;
    @InjectMocks TeamService service;

    @org.junit.jupiter.api.BeforeEach
    void ownerPlanIncludesTeam() {
        org.mockito.Mockito.lenient().when(plans.ownerHasTeam(org.mockito.ArgumentMatchers.any())).thenReturn(true);
    }

    private Property property(UUID propertyId, UUID ownerId) {
        return Property.builder().propertyId(propertyId).user(User.builder().userId(ownerId).build())
                .teamEnabled(true).build();
    }

    @Test
    void viewerCanReadButCannotEditOrDelete() {
        UUID propertyId = UUID.randomUUID(), ownerId = UUID.randomUUID(), viewerId = UUID.randomUUID();
        when(properties.findById(propertyId)).thenReturn(Optional.of(property(propertyId, ownerId)));
        when(members.findByProperty_PropertyIdAndUser_UserId(propertyId, viewerId))
                .thenReturn(Optional.of(PropertyMember.builder().role(TeamRole.VIEWER).build()));

        assertThat(service.requireView(propertyId, viewerId)).isNotNull();
        assertThatThrownBy(() -> service.requireEdit(propertyId, viewerId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(() -> service.requireAdmin(propertyId, viewerId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }

    @Test
    void editorCanEditButCannotDelete() {
        UUID propertyId = UUID.randomUUID(), ownerId = UUID.randomUUID(), editorId = UUID.randomUUID();
        when(properties.findById(propertyId)).thenReturn(Optional.of(property(propertyId, ownerId)));
        when(members.findByProperty_PropertyIdAndUser_UserId(propertyId, editorId))
                .thenReturn(Optional.of(PropertyMember.builder().role(TeamRole.EDITOR).build()));

        assertThat(service.requireEdit(propertyId, editorId)).isNotNull();
        assertThatThrownBy(() -> service.requireAdmin(propertyId, editorId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }

    @Test
    void disablingTeamRevokesMemberAccess() {
        UUID propertyId = UUID.randomUUID(), ownerId = UUID.randomUUID(), viewerId = UUID.randomUUID();
        when(properties.findById(propertyId)).thenReturn(Optional.of(Property.builder()
                .propertyId(propertyId).user(User.builder().userId(ownerId).build()).teamEnabled(false).build()));
        assertThatThrownBy(() -> service.requireView(propertyId, viewerId))
                .isInstanceOf(br.com.sentinela.exception.PropertyNotFoundException.class);
        verifyNoInteractions(members);
    }

    @Test
    void invitationCannotBeAcceptedByAnotherEmail() {
        UUID userId = UUID.randomUUID();
        String token = "A".repeat(43);
        when(users.findById(userId)).thenReturn(Optional.of(User.builder().userId(userId).email("other@example.com").build()));
        when(invitations.findByTokenHash(anyString())).thenReturn(Optional.of(PropertyInvitation.builder()
                .email("invited@example.com").expiresAt(LocalDateTime.now().plusDays(1)).build()));

        assertThatThrownBy(() -> service.accept(userId, token))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        verifyNoInteractions(members);
    }

    @Test
    void memberCanLeaveWithoutAdminPermission() {
        UUID propertyId = UUID.randomUUID(), ownerId = UUID.randomUUID(), memberId = UUID.randomUUID();
        PropertyMember membership = PropertyMember.builder().role(TeamRole.VIEWER).build();
        when(properties.findById(propertyId)).thenReturn(Optional.of(property(propertyId, ownerId)));
        when(members.findByProperty_PropertyIdAndUser_UserId(propertyId, memberId))
                .thenReturn(Optional.of(membership));

        service.leave(propertyId, memberId);

        verify(members).delete(membership);
    }

    @Test
    void ownerCannotLeaveOwnProperty() {
        UUID propertyId = UUID.randomUUID(), ownerId = UUID.randomUUID();
        when(properties.findById(propertyId)).thenReturn(Optional.of(property(propertyId, ownerId)));

        assertThatThrownBy(() -> service.leave(propertyId, ownerId))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        verify(members, never()).delete(any());
    }

    @Test
    void editorCannotRenameTeam() {
        UUID propertyId = UUID.randomUUID(), ownerId = UUID.randomUUID(), editorId = UUID.randomUUID();
        when(properties.findById(propertyId)).thenReturn(Optional.of(property(propertyId, ownerId)));
        when(members.findByProperty_PropertyIdAndUser_UserId(propertyId, editorId))
                .thenReturn(Optional.of(PropertyMember.builder().role(TeamRole.EDITOR).build()));

        assertThatThrownBy(() -> service.changeTitle(propertyId, editorId, "Equipe Norte"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        verify(properties, never()).saveAndFlush(any());
    }
}
