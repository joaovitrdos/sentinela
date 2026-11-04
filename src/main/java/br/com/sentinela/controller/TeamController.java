package br.com.sentinela.controller;

import br.com.sentinela.dto.request.*;
import br.com.sentinela.dto.response.TeamOverviewDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor
public class TeamController {
    private final TeamService teamService;

    @GetMapping("/api/properties/{propertyId}/team")
    public TeamOverviewDto overview(@AuthenticationPrincipal User user, @PathVariable UUID propertyId) {
        return teamService.overview(propertyId, user.getUserId());
    }

    @PutMapping("/api/properties/{propertyId}/team/mode")
    public TeamOverviewDto mode(@AuthenticationPrincipal User user, @PathVariable UUID propertyId,
                                @Valid @RequestBody TeamModeRequestDto request) {
        return teamService.setMode(propertyId, user.getUserId(), request.enabled());
    }

    @PutMapping("/api/properties/{propertyId}/team/title")
    public TeamOverviewDto title(@AuthenticationPrincipal User user, @PathVariable UUID propertyId,
                                 @Valid @RequestBody TeamTitleRequestDto request) {
        return teamService.changeTitle(propertyId, user.getUserId(), request.title());
    }

    @DeleteMapping("/api/properties/{propertyId}/team/me")
    public void leave(@AuthenticationPrincipal User user, @PathVariable UUID propertyId) {
        teamService.leave(propertyId, user.getUserId());
    }

    @PostMapping("/api/properties/{propertyId}/team/invitations")
    public TeamOverviewDto invite(@AuthenticationPrincipal User user, @PathVariable UUID propertyId,
                                  @Valid @RequestBody TeamInviteRequestDto request) {
        return teamService.invite(propertyId, user.getUserId(), request);
    }

    @DeleteMapping("/api/properties/{propertyId}/team/invitations/{invitationId}")
    public TeamOverviewDto cancel(@AuthenticationPrincipal User user, @PathVariable UUID propertyId,
                                  @PathVariable UUID invitationId) {
        return teamService.cancelInvitation(propertyId, user.getUserId(), invitationId);
    }

    @PutMapping("/api/properties/{propertyId}/team/members/{memberId}")
    public TeamOverviewDto role(@AuthenticationPrincipal User user, @PathVariable UUID propertyId,
                                @PathVariable UUID memberId, @Valid @RequestBody TeamRoleRequestDto request) {
        return teamService.changeRole(propertyId, user.getUserId(), memberId, request.role());
    }

    @DeleteMapping("/api/properties/{propertyId}/team/members/{memberId}")
    public TeamOverviewDto remove(@AuthenticationPrincipal User user, @PathVariable UUID propertyId,
                                  @PathVariable UUID memberId) {
        return teamService.removeMember(propertyId, user.getUserId(), memberId);
    }

    @PostMapping("/api/team-invitations/accept")
    public void accept(@AuthenticationPrincipal User user, @Valid @RequestBody TeamAcceptRequestDto request) {
        teamService.accept(user.getUserId(), request.token());
    }
}
