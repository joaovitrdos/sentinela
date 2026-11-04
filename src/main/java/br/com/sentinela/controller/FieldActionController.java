package br.com.sentinela.controller;

import br.com.sentinela.dto.request.*;
import br.com.sentinela.dto.response.FieldActionResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.service.FieldActionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/properties/{propertyId}/actions") @RequiredArgsConstructor
public class FieldActionController {
    private final FieldActionService actionService;

    @GetMapping
    public List<FieldActionResponseDto> list(@AuthenticationPrincipal User user, @PathVariable UUID propertyId) {
        return actionService.list(user.getUserId(), propertyId);
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public FieldActionResponseDto create(@AuthenticationPrincipal User user, @PathVariable UUID propertyId,
                                         @Valid @RequestBody FieldActionRequestDto request) {
        return actionService.create(user.getUserId(), propertyId, request);
    }

    @PostMapping("/{actionId}/complete")
    public FieldActionResponseDto complete(@AuthenticationPrincipal User user, @PathVariable UUID propertyId,
                                           @PathVariable UUID actionId, @Valid @RequestBody CompleteActionRequestDto request) {
        return actionService.complete(user.getUserId(), propertyId, actionId, request);
    }

    @DeleteMapping("/{actionId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable UUID propertyId, @PathVariable UUID actionId) {
        actionService.delete(user.getUserId(), propertyId, actionId);
    }
}
