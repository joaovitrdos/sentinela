package br.com.sentinela.service;

import br.com.sentinela.dto.request.GoogleAuthRequestDto;
import br.com.sentinela.dto.request.LoginRequestDto;
import br.com.sentinela.dto.request.RegisterRequestDto;
import br.com.sentinela.dto.request.SendEmailRequestDto;
import br.com.sentinela.dto.request.ValidateCodeRequestDto;
import br.com.sentinela.dto.response.AuthResponseDto;
import br.com.sentinela.dto.response.MessageResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.enums.AuthProvider;
import br.com.sentinela.enums.CodePurpose;
import br.com.sentinela.exception.EmailAlreadyRegisteredException;
import br.com.sentinela.exception.EmailNotVerifiedException;
import br.com.sentinela.exception.GoogleEmailNotVerifiedException;
import br.com.sentinela.exception.InvalidCredentialsException;
import br.com.sentinela.exception.InvalidGoogleTokenException;
import br.com.sentinela.exception.InvalidRegistrationDataException;
import br.com.sentinela.exception.UserSaveException;
import br.com.sentinela.infra.security.AttemptGuard;
import br.com.sentinela.infra.security.TokenService;
import br.com.sentinela.mapper.UserMapper;
import br.com.sentinela.notification.EmailFactory;
import br.com.sentinela.notification.EmailRequestedEvent;
import br.com.sentinela.repository.UserRepository;
import br.com.sentinela.util.EmailNormalizer;
import br.com.sentinela.util.LogUtil;
import br.com.sentinela.util.Util;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final EmailFactory emailFactory;
    private final ApplicationEventPublisher events;
    private final UserMapper userMapper;
    private final TokenService tokenService;
    private final AttemptGuard attemptGuard;
    private final VerificationCodeService verificationCodeService;
    private String decoyHash;
    
    private static final String REGISTER_MESSAGE = "Enviamos um código de confirmação para o seu e-mail";
    private static final String RESEND_MESSAGE = "Se este e-mail estiver disponível, enviaremos um código de confirmação para ele";


    @PostConstruct
    void prepareDecoyHash() {
        decoyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public MessageResponseDto register(RegisterRequestDto request) {
        return register(request, null);
    }

    @Transactional
    public MessageResponseDto register(RegisterRequestDto request, String clientAddress) {
        validateRegister(request);

        String email = normalizeEmail(request.email());
        attemptGuard.checkRecovery(email, clientAddress);

        Optional<User> existing = userRepository.findByEmail(email);

        if (existing.isPresent()) {
            User user = existing.get();

            if (user.isEmailVerified() || !user.hasLocalPassword()) {
                throw new EmailAlreadyRegisteredException();
            }

            sendVerificationCode(user);
            return new MessageResponseDto(REGISTER_MESSAGE);
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .authProvider(AuthProvider.LOCAL)
                .build();

        sendVerificationCode(persistNewUser(user));

        return new MessageResponseDto(REGISTER_MESSAGE);
    }

    @Transactional
    public MessageResponseDto resendVerification(SendEmailRequestDto request) {
        return resendVerification(request, null);
    }

    @Transactional
    public MessageResponseDto resendVerification(SendEmailRequestDto request, String clientAddress) {
        String email = normalizeEmail(request.email());
        attemptGuard.checkRecovery(email, clientAddress);

        userRepository.findByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .filter(User::hasLocalPassword)
                .ifPresent(this::sendVerificationCode);

        return new MessageResponseDto(RESEND_MESSAGE);
    }

    @Transactional
    public AuthResponseDto confirmEmail(ValidateCodeRequestDto request) {
        return confirmEmail(request, null);
    }

    @Transactional
    public AuthResponseDto confirmEmail(ValidateCodeRequestDto request, String clientAddress) {
        String email = normalizeEmail(request.email());
        attemptGuard.checkCodeAttempt(email, clientAddress);

        User user = userRepository.findByEmail(email).orElseThrow(this::invalidCredentials);

        verificationCodeService.consumeCode(request.code(), email, CodePurpose.EMAIL_VERIFICATION);

        user.setEmailVerified(true);
        User confirmed = saveUser(user);

        attemptGuard.clearCodeAttempts(email, clientAddress);
        sendWelcomeEmail(confirmed);

        return issueToken(confirmed);
    }

    @Transactional
    public AuthResponseDto login(LoginRequestDto request) {
        String email = normalizeEmail(request.email());
        attemptGuard.checkLogin(email);

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null || !user.hasLocalPassword()
                || !passwordEncoder.matches(request.password(), user.getPassword())) {
            burnPasswordComparison(request.password());
            throw failedLogin(email);
        }

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }

        attemptGuard.clearLogin(email);

        return issueToken(user);
    }

    @Transactional
    public AuthResponseDto loginWithGoogle(GoogleAuthRequestDto request) {
        return issueToken(resolveGoogleUser(request));
    }

    @Transactional
    public void logout(User principal) {
        User user = userRepository.findById(principal.getUserId()).orElseThrow(this::invalidCredentials);
        user.revokeIssuedTokens();
        saveUser(user);
        SecurityContextHolder.clearContext();
    }

    @Transactional
    public void logoutEverywhere(User user) {
        logout(user);
    }

    private User persistNewUser(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Outro cadastro com o mesmo e-mail entrou entre a consulta e o insert
            throw new EmailAlreadyRegisteredException();
        } catch (DataAccessException ex) {
            LogUtil.error("Falha ao cadastrar o usuário", ex);
            throw new UserSaveException(ex);
        }
    }

    private void sendVerificationCode(User user) {
        String code = verificationCodeService.createCode(user, CodePurpose.EMAIL_VERIFICATION);
        events.publishEvent(new EmailRequestedEvent(
                emailFactory.createEmailVerification(user.getEmail(), code), user));
    }

    private void validateRegister(RegisterRequestDto request) {
        if (Util.isNullOrEmpty(request.name())) {
            throw new InvalidRegistrationDataException();
        }
        if (Util.isNullOrEmpty(request.email())) {
            throw new InvalidRegistrationDataException();
        }
        if (Util.isNullOrEmpty(request.password())) {
            throw new InvalidRegistrationDataException();
        }
    }

    private InvalidCredentialsException failedLogin(String email) {
        attemptGuard.recordLoginFailure(email);
        return invalidCredentials();
    }

    private InvalidCredentialsException invalidCredentials() {
        return new InvalidCredentialsException();
    }

    private void burnPasswordComparison(String rawPassword) {
        passwordEncoder.matches(rawPassword, decoyHash);
    }

    private User resolveGoogleUser(GoogleAuthRequestDto request) {
        GoogleIdToken.Payload payload = googleTokenVerifier.verify(request.idToken());

        String googleId = requiredClaim(payload.getSubject());
        String email = normalizeEmail(requiredClaim(payload.getEmail()));
        requireVerifiedEmail(payload);
        String name = optionalClaim(payload, "name");
        String picture = optionalClaim(payload, "picture");

        return userRepository.findByGoogleId(googleId)
                .map(user -> refreshGoogleProfile(user, name, picture))
                .orElseGet(() -> userRepository.findByEmail(email)
                        .map(user -> linkGoogleAccount(user, googleId, picture))
                        .orElseGet(() -> createFromGoogle(googleId, email, name, picture)));
    }

    private void requireVerifiedEmail(GoogleIdToken.Payload payload) {
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new GoogleEmailNotVerifiedException();
        }
    }

    private AuthResponseDto issueToken(User user) {
        return AuthResponseDto.bearer(tokenService.generateToken(user), userMapper.toResponse(user));
    }

    private User createFromGoogle(String googleId, String email, String name, String picture) {
        User user = User.builder()
                .name(name != null ? name : email)
                .email(email)
                .picture(picture)
                .googleId(googleId)
                .emailVerified(true)
                .authProvider(AuthProvider.GOOGLE)
                .build();
        User created = saveUser(user);
        sendWelcomeEmail(created);
        return created;
    }

    private User linkGoogleAccount(User user, String googleId, String picture) {
        user.setGoogleId(googleId);
        user.setEmailVerified(true);
        if (picture != null) {
            user.setPicture(picture);
        }
        User linked = saveUser(user);
        sendWelcomeEmail(linked);
        return linked;
    }

    private void sendWelcomeEmail(User user) {
        events.publishEvent(new EmailRequestedEvent(
                emailFactory.createWelcomeEmail(user.getEmail(), user.getName()), user));
    }

    private User refreshGoogleProfile(User user, String name, String picture) {
        if (name != null) {
            user.setName(name);
        }
        if (picture != null) {
            user.setPicture(picture);
        }
        return saveUser(user);
    }

    private User saveUser(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataAccessException ex) {
            LogUtil.error("Falha ao salvar o usuário " + user.getUserId(), ex);
            throw new UserSaveException(ex);
        }
    }

    private String requiredClaim(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidGoogleTokenException();
        }
        return value;
    }

    private String optionalClaim(GoogleIdToken.Payload payload, String claim) {
        Object value = payload.get(claim);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private String normalizeEmail(String email) {
        return EmailNormalizer.normalize(email);
    }
}
