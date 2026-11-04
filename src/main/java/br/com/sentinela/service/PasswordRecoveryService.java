package br.com.sentinela.service;

import br.com.sentinela.dto.request.ChangePasswordRequestDto;
import br.com.sentinela.dto.request.ResetPasswordRequestDto;
import br.com.sentinela.dto.request.SendEmailRequestDto;
import br.com.sentinela.dto.request.ValidateCodeRequestDto;
import br.com.sentinela.dto.response.AuthResponseDto;
import br.com.sentinela.dto.response.MessageResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.enums.AuthProvider;
import br.com.sentinela.enums.CodePurpose;
import br.com.sentinela.exception.GoogleAccountWithoutPasswordException;
import br.com.sentinela.exception.IncorrectPasswordException;
import br.com.sentinela.exception.InvalidVerificationCodeException;
import br.com.sentinela.exception.UserSaveException;
import br.com.sentinela.infra.security.AttemptGuard;
import br.com.sentinela.infra.security.TokenService;
import br.com.sentinela.mapper.UserMapper;
import br.com.sentinela.notification.EmailFactory;
import br.com.sentinela.notification.EmailRequestedEvent;
import br.com.sentinela.repository.UserRepository;
import br.com.sentinela.util.EmailNormalizer;
import br.com.sentinela.util.LogUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordRecoveryService {

    private static final String GENERIC_SENT =
            "Se o e-mail estiver cadastrado, enviaremos um código de verificação";

    private final UserRepository userRepository;
    private final VerificationCodeService verificationCodeService;
    private final EmailFactory emailFactory;
    private final ApplicationEventPublisher events;
    private final PasswordEncoder passwordEncoder;
    private final AttemptGuard attemptGuard;
    private final TokenService tokenService;
    private final UserMapper userMapper;

    @Transactional
    public MessageResponseDto sendRecoveryCode(SendEmailRequestDto request) {
        String email = EmailNormalizer.normalize(request.email());
        attemptGuard.checkRecovery(email);

        userRepository.findByEmail(email)
                .filter(User::hasLocalPassword)
                .ifPresent(this::sendCode);

        return new MessageResponseDto(GENERIC_SENT);
    }

    @Transactional
    public void validateCode(ValidateCodeRequestDto request) {
        String email = EmailNormalizer.normalize(request.email());
        attemptGuard.checkCodeAttempt(email);

        verificationCodeService.validateCode(request.code(), email, CodePurpose.PASSWORD_RECOVERY);
    }

    @Transactional
    public MessageResponseDto resetPassword(ResetPasswordRequestDto request) {
        String email = EmailNormalizer.normalize(request.email());
        attemptGuard.checkCodeAttempt(email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidVerificationCodeException::new);

        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            throw new InvalidVerificationCodeException();
        }

        verificationCodeService.consumeCode(request.code(), email, CodePurpose.PASSWORD_RECOVERY);

        applyNewPassword(user, request.newPassword());
        attemptGuard.clearCodeAttempts(email);
        attemptGuard.clearLogin(email);

        return new MessageResponseDto("Senha atualizada com sucesso");
    }

    /**
     * Troca a senha e revoga os tokens emitidos (os outros dispositivos saem),
     * mas devolve um token novo para a sessao atual continuar logada.
     */
    @Transactional
    public AuthResponseDto changePassword(User user, ChangePasswordRequestDto request) {
        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            throw new GoogleAccountWithoutPasswordException();
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IncorrectPasswordException();
        }

        applyNewPassword(user, request.newPassword());

        return AuthResponseDto.bearer(tokenService.generateToken(user), userMapper.toResponse(user));
    }

    private void applyNewPassword(User user, String rawPassword) {
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.revokeIssuedTokens();

        try {
            userRepository.saveAndFlush(user);
        } catch (DataAccessException ex) {
            LogUtil.error("Falha ao salvar a nova senha do usuário " + user.getUserId(), ex);
            throw new UserSaveException(ex);
        }
    }

    private void sendCode(User user) {
        String code = verificationCodeService.createCode(user, CodePurpose.PASSWORD_RECOVERY);
        events.publishEvent(new EmailRequestedEvent(
                emailFactory.createPasswordRecovery(user.getEmail(), code), user));
    }
}
