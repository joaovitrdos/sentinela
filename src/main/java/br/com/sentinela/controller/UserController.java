package br.com.sentinela.controller;

import br.com.sentinela.dto.request.ChangeEmailRequestDto;
import br.com.sentinela.dto.request.ChangePasswordRequestDto;
import br.com.sentinela.dto.request.ConfirmEmailChangeRequestDto;
import br.com.sentinela.dto.request.DeleteAccountRequestDto;
import br.com.sentinela.dto.request.UpdateUserRequestDto;
import br.com.sentinela.dto.response.AuthResponseDto;
import br.com.sentinela.dto.response.MessageResponseDto;
import br.com.sentinela.dto.response.PublicUserResponseDto;
import br.com.sentinela.dto.response.UserResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.service.PasswordRecoveryService;
import br.com.sentinela.service.UserService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PasswordRecoveryService passwordRecoveryService;

    @GetMapping("/me")
    public UserResponseDto me(
            @AuthenticationPrincipal User principal) {
        return userService.findById(principal.getUserId());
    }

    @PutMapping("/me")
    public UserResponseDto update(
            @AuthenticationPrincipal User principal,
            @RequestBody @Valid UpdateUserRequestDto request) {
        return userService.updateProfile(principal.getUserId(), request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User principal, @RequestBody @Valid DeleteAccountRequestDto request) {
        userService.delete(principal.getUserId(), request);
    }

    @PostMapping("/me/email")
    public MessageResponseDto requestEmailChange(
            @AuthenticationPrincipal User principal,
            @RequestBody @Valid ChangeEmailRequestDto request,
            HttpServletRequest servletRequest) {
        return userService.requestEmailChange(principal, request, servletRequest.getRemoteAddr());
    }

    @PostMapping("/me/email/confirm")
    public AuthResponseDto confirmEmailChange(
            @AuthenticationPrincipal User principal,
            @RequestBody @Valid ConfirmEmailChangeRequestDto request,
            HttpServletRequest servletRequest) {
        return userService.confirmEmailChange(principal, request, servletRequest.getRemoteAddr());
    }

    @PostMapping("/change-password")
    public AuthResponseDto changePassword(
            @AuthenticationPrincipal User principal,
            @RequestBody @Valid ChangePasswordRequestDto request) {
        return passwordRecoveryService.changePassword(principal, request);
    }

    @GetMapping("/{userId}")
    public PublicUserResponseDto findById(
            @AuthenticationPrincipal User principal,
            @PathVariable UUID userId) {
        return userService.findPublicById(principal.getUserId(), userId);
    }

    @GetMapping("/count")
    public long count() {
        return userService.count();
    }
}
