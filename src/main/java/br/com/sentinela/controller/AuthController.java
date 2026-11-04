package br.com.sentinela.controller;

import br.com.sentinela.dto.request.GoogleAuthRequestDto;
import br.com.sentinela.dto.request.LoginRequestDto;
import br.com.sentinela.dto.request.RegisterRequestDto;
import br.com.sentinela.dto.request.ResetPasswordRequestDto;
import br.com.sentinela.dto.request.SendEmailRequestDto;
import br.com.sentinela.dto.request.ValidateCodeRequestDto;
import br.com.sentinela.dto.response.AuthResponseDto;
import br.com.sentinela.dto.response.MessageResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.service.AuthService;
import br.com.sentinela.service.PasswordRecoveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordRecoveryService passwordRecoveryService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponseDto register(@RequestBody @Valid RegisterRequestDto request, HttpServletRequest servletRequest) {
        return authService.register(request, servletRequest.getRemoteAddr());
    }

    @PostMapping("/email/confirm")
    public AuthResponseDto confirmEmail(@RequestBody @Valid ValidateCodeRequestDto request, HttpServletRequest servletRequest) {
        return authService.confirmEmail(request, servletRequest.getRemoteAddr());
    }

    @PostMapping("/email/resend")
    public MessageResponseDto resendVerification(@RequestBody @Valid SendEmailRequestDto request, HttpServletRequest servletRequest) {
        return authService.resendVerification(request, servletRequest.getRemoteAddr());
    }

    @PostMapping("/login")
    public AuthResponseDto login(@RequestBody @Valid LoginRequestDto request) {
        return authService.login(request);
    }

    @PostMapping("/google")
    public AuthResponseDto loginWithGoogle(@RequestBody @Valid GoogleAuthRequestDto request) {
        return authService.loginWithGoogle(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal User principal) {
        authService.logout(principal);
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutEverywhere(@AuthenticationPrincipal User principal) {
        authService.logoutEverywhere(principal);
    }

    @PostMapping("/password/forgot")
    public MessageResponseDto forgotPassword(@RequestBody @Valid SendEmailRequestDto request, HttpServletRequest servletRequest) {
        return passwordRecoveryService.sendRecoveryCode(request, servletRequest.getRemoteAddr());
    }

    @PostMapping("/password/validate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void validateRecoveryCode(@RequestBody @Valid ValidateCodeRequestDto request, HttpServletRequest servletRequest) {
        passwordRecoveryService.validateCode(request, servletRequest.getRemoteAddr());
    }

    @PostMapping("/password/reset")
    public MessageResponseDto resetPassword(@RequestBody @Valid ResetPasswordRequestDto request, HttpServletRequest servletRequest) {
        return passwordRecoveryService.resetPassword(request, servletRequest.getRemoteAddr());
    }
}
