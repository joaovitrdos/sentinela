package br.com.sentinela.service;

import br.com.sentinela.dto.request.ChangeEmailRequestDto;
import br.com.sentinela.dto.request.ConfirmEmailChangeRequestDto;
import br.com.sentinela.dto.request.DeleteAccountRequestDto;
import br.com.sentinela.dto.request.UpdateUserRequestDto;
import br.com.sentinela.dto.response.AuthResponseDto;
import br.com.sentinela.dto.response.MessageResponseDto;
import br.com.sentinela.dto.response.PublicUserResponseDto;
import br.com.sentinela.dto.response.UserResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.entity.VerificationCode;
import br.com.sentinela.enums.AuthProvider;
import br.com.sentinela.enums.CodePurpose;
import br.com.sentinela.exception.EmailAlreadyRegisteredException;
import br.com.sentinela.exception.GoogleAccountEmailChangeException;
import br.com.sentinela.exception.IncorrectPasswordException;
import br.com.sentinela.exception.UserDeleteException;
import br.com.sentinela.exception.UserNotFoundException;
import br.com.sentinela.exception.UserSaveException;
import br.com.sentinela.infra.config.CacheConfig;
import br.com.sentinela.infra.security.AttemptGuard;
import br.com.sentinela.infra.security.TokenService;
import br.com.sentinela.mapper.UserMapper;
import br.com.sentinela.notification.EmailFactory;
import br.com.sentinela.notification.EmailRequestedEvent;
import br.com.sentinela.repository.EmailRepository;
import br.com.sentinela.repository.PropertyRepository;
import br.com.sentinela.repository.PropertyActivityRepository;
import br.com.sentinela.repository.PlotRepository;
import br.com.sentinela.repository.FieldActionRepository;
import br.com.sentinela.repository.PropertyMemberRepository;
import br.com.sentinela.repository.UserRepository;
import br.com.sentinela.util.EmailNormalizer;
import br.com.sentinela.util.LogUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EmailRepository emailRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyActivityRepository propertyActivityRepository;
    private final PlotRepository plotRepository;
    private final FieldActionRepository fieldActionRepository;
    private final PropertyMemberRepository propertyMemberRepository;
    private final UserMapper userMapper;
    private final VerificationCodeService verificationCodeService;
    private final EmailFactory emailFactory;
    private final ApplicationEventPublisher events;
    private final PasswordEncoder passwordEncoder;
    private final AttemptGuard attemptGuard;
    private final TokenService tokenService;
    private final GoogleTokenVerifier googleTokenVerifier;

    @Transactional(readOnly = true)
    public UserResponseDto findById(UUID userId) {
        return userMapper.toResponse(findEntityById(userId));
    }

    @Transactional(readOnly = true)
    public PublicUserResponseDto findPublicById(UUID requesterId, UUID userId) {
        if (requesterId == null || !requesterId.equals(userId)) {
            throw new UserNotFoundException();
        }
        return userMapper.toPublicResponse(findEntityById(userId));
    }

    @Transactional
    public UserResponseDto updateProfile(UUID userId, UpdateUserRequestDto request) {
        User user = findEntityById(userId);

        user.setName(request.name().trim());
        user.setPicture(StringUtils.hasText(request.picture()) ? request.picture().trim() : null);

        return userMapper.toResponse(saveUser(user));
    }

    @Transactional
    public MessageResponseDto requestEmailChange(User loggedUser, ChangeEmailRequestDto request) {
        return requestEmailChange(loggedUser, request, null);
    }

    @Transactional
    public MessageResponseDto requestEmailChange(User loggedUser, ChangeEmailRequestDto request, String clientAddress) {
        User user = findEntityById(loggedUser.getUserId());

        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            throw new GoogleAccountEmailChangeException();
        }
        attemptGuard.checkLogin(user.getEmail());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            attemptGuard.recordLoginFailure(user.getEmail());
            throw new IncorrectPasswordException();
        }
        attemptGuard.clearLogin(user.getEmail());

        String newEmail = EmailNormalizer.normalize(request.newEmail());
        attemptGuard.checkRecovery(newEmail, clientAddress);

        if (userRepository.existsByEmail(newEmail)) {
            return new MessageResponseDto("Enviamos um código de confirmação para o novo endereço");
        }

        String code = verificationCodeService.createCode(user, CodePurpose.EMAIL_CHANGE, newEmail);
        events.publishEvent(new EmailRequestedEvent(emailFactory.createEmailChange(newEmail, code), user));

        return new MessageResponseDto("Enviamos um código de confirmação para o novo endereço");
    }

    /**
     * Confirma o novo e-mail e revoga os tokens emitidos,
     * devolvendo um token novo para a sessao atual continuar logada.
     */
    @Transactional
    public AuthResponseDto confirmEmailChange(User loggedUser, ConfirmEmailChangeRequestDto request) {
        return confirmEmailChange(loggedUser, request, null);
    }

    @Transactional
    public AuthResponseDto confirmEmailChange(User loggedUser, ConfirmEmailChangeRequestDto request, String clientAddress) {
        User user = findEntityById(loggedUser.getUserId());
        attemptGuard.checkCodeAttempt(user.getEmail(), clientAddress);

        VerificationCode verification =
                verificationCodeService.consumeCode(request.code(), user.getEmail(), CodePurpose.EMAIL_CHANGE);

        String newEmail = EmailNormalizer.normalize(verification.getTargetEmail());

        if (userRepository.existsByEmail(newEmail)) {
            throw new EmailAlreadyRegisteredException();
        }

        user.setEmail(newEmail);
        user.setEmailVerified(true);
        user.revokeIssuedTokens();

        User saved = saveUser(user);
        attemptGuard.clearCodeAttempts(user.getEmail(), clientAddress);
        return AuthResponseDto.bearer(tokenService.generateToken(saved), userMapper.toResponse(saved));
    }

    @Transactional
    public void delete(UUID userId, DeleteAccountRequestDto request) {
        User user = findEntityById(userId);
        verifyDeleteReauthentication(user, request);

        try {
            emailRepository.deleteByUserId(userId);
            propertyMemberRepository.deleteAllByUser_UserId(userId);
            propertyActivityRepository.deleteAllByProperty_User_UserId(userId);
            fieldActionRepository.deleteAllByProperty_User_UserId(userId);
            plotRepository.deleteAllByProperty_User_UserId(userId);
            propertyRepository.deleteAllOwnedBy(userId);
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataAccessException ex) {
            LogUtil.error("Falha ao excluir o usuário " + userId, ex);
            throw new UserDeleteException(ex);
        }
    }

    private void verifyDeleteReauthentication(User user, DeleteAccountRequestDto request) {
        if (request == null) {
            throw new IncorrectPasswordException();
        }
        if (user.getAuthProvider() == AuthProvider.LOCAL) {
            String email = EmailNormalizer.normalize(user.getEmail());
            attemptGuard.checkLogin(email);
            if (request.currentPassword() == null
                    || !passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
                attemptGuard.recordLoginFailure(email);
                throw new IncorrectPasswordException();
            }
            attemptGuard.clearLogin(email);
            return;
        }

        if (request.googleIdToken() == null || user.getGoogleId() == null) {
            throw new IncorrectPasswordException();
        }
        var payload = googleTokenVerifier.verify(request.googleIdToken());
        if (!user.getGoogleId().equals(payload.getSubject()) || !Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new IncorrectPasswordException();
        }
    }

    private User saveUser(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyRegisteredException();
        } catch (DataAccessException ex) {
            LogUtil.error("Falha ao salvar o usuário " + user.getUserId(), ex);
            throw new UserSaveException(ex);
        }
    }

    private User findEntityById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }

    @Cacheable(CacheConfig.USER_COUNT)
    public long count() {
        return userRepository.count();
    }
}
