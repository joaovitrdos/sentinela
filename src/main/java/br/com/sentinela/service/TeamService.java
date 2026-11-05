package br.com.sentinela.service;

import br.com.sentinela.dto.request.TeamInviteRequestDto;
import br.com.sentinela.dto.response.TeamOverviewDto;
import br.com.sentinela.entity.Property;
import br.com.sentinela.entity.PropertyInvitation;
import br.com.sentinela.entity.PropertyMember;
import br.com.sentinela.entity.User;
import br.com.sentinela.enums.TeamRole;
import br.com.sentinela.exception.PropertyNotFoundException;
import br.com.sentinela.notification.EmailFactory;
import br.com.sentinela.notification.EmailRequestedEvent;
import br.com.sentinela.repository.PropertyInvitationRepository;
import br.com.sentinela.repository.PropertyMemberRepository;
import br.com.sentinela.repository.PropertyRepository;
import br.com.sentinela.repository.UserRepository;
import br.com.sentinela.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class TeamService {
    private final PropertyRepository properties;
    private final PropertyMemberRepository members;
    private final PropertyInvitationRepository invitations;
    private final UserRepository users;
    private final EmailFactory emailFactory;
    private final ApplicationEventPublisher events;
    private final PlanService plans;
    private final SecureRandom random = new SecureRandom();

    @Value("${sentinela.web-base-url:http://localhost:5173}")
    private String webBaseUrl;

    @Transactional(readOnly = true)
    public Property requireView(UUID propertyId, UUID userId) {
        Property property = properties.findById(propertyId).orElseThrow(PropertyNotFoundException::new);
        if (role(property, userId) == null) throw new PropertyNotFoundException();
        return property;
    }

    @Transactional(readOnly = true)
    public Property requireEdit(UUID propertyId, UUID userId) {
        Property property = requireView(propertyId, userId);
        if (!role(property, userId).canEdit()) throw forbidden();
        return property;
    }

    @Transactional(readOnly = true)
    public Property requireAdmin(UUID propertyId, UUID userId) {
        Property property = requireView(propertyId, userId);
        if (!role(property, userId).canDelete()) throw forbidden();
        return property;
    }

    @Transactional(readOnly = true)
    public Property requireOwner(UUID propertyId, UUID userId) {
        Property property = requireView(propertyId, userId);
        if (!property.getUser().getUserId().equals(userId)) throw forbidden();
        return property;
    }

    @Transactional(readOnly = true)
    public TeamRole role(Property property, UUID userId) {
        if (userId == null || property == null) return null;
        if (property.getUser().getUserId().equals(userId)) return TeamRole.ADMIN;
        if (!Boolean.TRUE.equals(property.getTeamEnabled()) || !plans.ownerHasTeam(property.getUser().getUserId())) return null;
        return members.findByProperty_PropertyIdAndUser_UserId(property.getPropertyId(), userId)
                .map(PropertyMember::getRole).orElse(null);
    }

    @Transactional(readOnly = true)
    public User requireActiveMember(Property property, UUID userId) {
        if (property.getUser().getUserId().equals(userId)) return property.getUser();
        if (!Boolean.TRUE.equals(property.getTeamEnabled()) || !plans.ownerHasTeam(property.getUser().getUserId())) throw forbidden();
        return members.findByProperty_PropertyIdAndUser_UserId(property.getPropertyId(), userId)
                .map(PropertyMember::getUser).orElseThrow(this::forbidden);
    }

    @Transactional(readOnly = true)
    public List<Assignee> assignees(UUID propertyId, UUID userId) {
        Property property = requireView(propertyId, userId);
        var result = new java.util.ArrayList<Assignee>();
        result.add(new Assignee(property.getUser().getUserId(), property.getUser().getName()));
        if (Boolean.TRUE.equals(property.getTeamEnabled()))
            members.findAllByProperty_PropertyIdOrderByJoinedAtAsc(propertyId).forEach(member ->
                    result.add(new Assignee(member.getUser().getUserId(), member.getUser().getName())));
        return result;
    }

    public record Assignee(UUID userId, String name) {}

    @Transactional(readOnly = true)
    public TeamOverviewDto overview(UUID propertyId, UUID userId) {
        Property property = requireView(propertyId, userId);
        boolean owner = property.getUser().getUserId().equals(userId);
        TeamRole current = role(property, userId);
        List<TeamOverviewDto.Member> memberRows = List.of();
        List<TeamOverviewDto.Invitation> invitationRows = List.of();
        if (owner) {
            var ownerUser = property.getUser();
            memberRows = new java.util.ArrayList<>();
            memberRows.add(new TeamOverviewDto.Member(ownerUser.getUserId(), ownerUser.getName(), ownerUser.getEmail(),
                    TeamRole.ADMIN, property.getCreatedAt(), true));
            for (PropertyMember member : members.findAllByProperty_PropertyIdOrderByJoinedAtAsc(propertyId)) {
                memberRows.add(new TeamOverviewDto.Member(member.getUser().getUserId(), member.getUser().getName(),
                        member.getUser().getEmail(), member.getRole(), member.getJoinedAt(), false));
            }
            invitationRows = invitations.findAllByProperty_PropertyIdOrderByCreatedAtDesc(propertyId).stream()
                    .map(invite -> new TeamOverviewDto.Invitation(invite.getInvitationId(), invite.getEmail(), invite.getRole(), invite.getExpiresAt()))
                    .toList();
        }
        return new TeamOverviewDto(propertyId, property.getName(), titleOf(property), Boolean.TRUE.equals(property.getTeamEnabled()), owner,
                current, memberRows, invitationRows);
    }

    private String titleOf(Property property) {
        return property.getTeamTitle() == null || property.getTeamTitle().isBlank()
                ? property.getName() : property.getTeamTitle();
    }

    @Transactional
    public TeamOverviewDto changeTitle(UUID propertyId, UUID actorId, String title) {
        Property property = requireOwner(propertyId, actorId);
        property.setTeamTitle(title.trim());
        properties.saveAndFlush(property);
        return overview(propertyId, actorId);
    }

    @Transactional
    public TeamOverviewDto setMode(UUID propertyId, UUID userId, boolean enabled) {
        Property property = requireOwner(propertyId, userId);
        if (!property.getUser().getUserId().equals(userId)) throw forbidden();
        if (enabled && !plans.ownerHasTeam(userId)) throw planRequired();
        if (!enabled) {
            invitations.deleteAllByProperty_PropertyId(propertyId);
            members.deleteAllByProperty_PropertyId(propertyId);
        }
        property.setTeamEnabled(enabled);
        properties.saveAndFlush(property);
        return overview(propertyId, userId);
    }

    @Transactional
    public TeamOverviewDto invite(UUID propertyId, UUID userId, TeamInviteRequestDto request) {
        Property property = requireOwner(propertyId, userId);
        if (!Boolean.TRUE.equals(property.getTeamEnabled())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ative a equipe nesta propriedade primeiro");
        }
        String email = EmailNormalizer.normalize(request.email());
        if (property.getUser().getEmail().equalsIgnoreCase(email) || users.findByEmail(email)
                .map(user -> members.existsByProperty_PropertyIdAndUser_UserId(propertyId, user.getUserId())).orElse(false)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta pessoa já participa da propriedade");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        PropertyInvitation invitation = invitations.findByProperty_PropertyIdAndEmail(propertyId, email)
                .orElseGet(() -> PropertyInvitation.builder().property(property).email(email).build());
        invitation.setRole(request.role());
        invitation.setTokenHash(hash(token));
        invitation.setExpiresAt(LocalDateTime.now().plusDays(7));
        invitations.saveAndFlush(invitation);
        String link = webBaseUrl.replaceAll("/+$", "") + "/settings?tab=team&invite=" + token;
        events.publishEvent(new EmailRequestedEvent(emailFactory.createTeamInvitation(email, titleOf(property), property.getName(),
                users.findById(userId).orElseThrow().getName(), link), users.getReferenceById(userId)));
        return overview(propertyId, userId);
    }

    @Transactional
    public TeamOverviewDto changeRole(UUID propertyId, UUID actorId, UUID memberId, TeamRole newRole) {
        Property property = requireOwner(propertyId, actorId);
        if (property.getUser().getUserId().equals(memberId)) throw forbidden();
        PropertyMember member = members.findByProperty_PropertyIdAndUser_UserId(propertyId, memberId)
                .orElseThrow(PropertyNotFoundException::new);
        member.setRole(newRole);
        members.saveAndFlush(member);
        return overview(propertyId, actorId);
    }

    @Transactional
    public TeamOverviewDto removeMember(UUID propertyId, UUID actorId, UUID memberId) {
        Property property = requireOwner(propertyId, actorId);
        if (property.getUser().getUserId().equals(memberId)) throw forbidden();
        members.delete(members.findByProperty_PropertyIdAndUser_UserId(propertyId, memberId)
                .orElseThrow(PropertyNotFoundException::new));
        members.flush();
        return overview(propertyId, actorId);
    }

    @Transactional
    public void leave(UUID propertyId, UUID userId) {
        Property property = requireView(propertyId, userId);
        if (property.getUser().getUserId().equals(userId)) throw forbidden();
        PropertyMember member = members.findByProperty_PropertyIdAndUser_UserId(propertyId, userId)
                .orElseThrow(PropertyNotFoundException::new);
        members.delete(member);
    }

    @Transactional
    public TeamOverviewDto cancelInvitation(UUID propertyId, UUID actorId, UUID invitationId) {
        requireOwner(propertyId, actorId);
        PropertyInvitation invitation = invitations.findById(invitationId)
                .filter(item -> item.getProperty().getPropertyId().equals(propertyId))
                .orElseThrow(PropertyNotFoundException::new);
        invitations.delete(invitation);
        invitations.flush();
        return overview(propertyId, actorId);
    }

    @Transactional
    public void accept(UUID userId, String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalidInvite();
        var user = users.findById(userId).orElseThrow(PropertyNotFoundException::new);
        PropertyInvitation invitation = invitations.findByTokenHash(hash(token)).orElseThrow(this::invalidInvite);
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now()) ||
                !invitation.getEmail().equals(EmailNormalizer.normalize(user.getEmail())) ||
                !Boolean.TRUE.equals(invitation.getProperty().getTeamEnabled())) throw invalidInvite();
        Property property = invitation.getProperty();
        if (property.getUser().getUserId().equals(userId)) throw invalidInvite();
        if (!plans.ownerHasTeam(property.getUser().getUserId())) throw planRequired();
        PropertyMember member = members.findByProperty_PropertyIdAndUser_UserId(property.getPropertyId(), userId)
                .orElseGet(() -> PropertyMember.builder().property(property).user(user).build());
        member.setRole(invitation.getRole());
        members.saveAndFlush(member);
        invitations.delete(invitation);
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Permissão insuficiente nesta propriedade");
    }
    private ResponseStatusException invalidInvite() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Convite inválido ou expirado para esta conta");
    }
    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ResponseStatusException planRequired() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Seu plano não inclui este recurso");
    }
}
