package br.com.sentinela.service;

import br.com.sentinela.dto.request.ChangePasswordRequestDto;
import br.com.sentinela.dto.request.ResetPasswordRequestDto;
import br.com.sentinela.dto.request.SendEmailRequestDto;
import br.com.sentinela.dto.response.AuthResponseDto;
import br.com.sentinela.dto.response.MessageResponseDto;
import br.com.sentinela.dto.response.UserResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.enums.AuthProvider;
import br.com.sentinela.enums.CodePurpose;
import br.com.sentinela.exception.GoogleAccountWithoutPasswordException;
import br.com.sentinela.exception.IncorrectPasswordException;
import br.com.sentinela.exception.InvalidVerificationCodeException;
import br.com.sentinela.infra.security.AttemptGuard;
import br.com.sentinela.infra.security.TokenService;
import br.com.sentinela.mapper.UserMapper;
import br.com.sentinela.notification.EmailFactory;
import br.com.sentinela.notification.EmailRequestedEvent;
import br.com.sentinela.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceTest {

    private static final String GENERIC_SENT = "Se o e-mail estiver cadastrado, enviaremos um código de verificação";

    @Mock private UserRepository userRepository;
    @Mock private VerificationCodeService verificationCodeService;
    @Mock private EmailFactory emailFactory;
    @Mock private ApplicationEventPublisher events;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AttemptGuard attemptGuard;
    @Mock private TokenService tokenService;
    @Mock private UserMapper userMapper;

    @InjectMocks
    private PasswordRecoveryService passwordRecoveryService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .userId(UUID.randomUUID())
                .email("qa@example.com")
                .password("hash-antigo")
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(true)
                .build();
    }

    @Test
    void pedidoDeRecuperacaoEnviaCodigoParaUsuarioComSenha() {
        when(userRepository.findByEmail("qa@example.com")).thenReturn(Optional.of(user));
        when(verificationCodeService.createCode(user, CodePurpose.PASSWORD_RECOVERY)).thenReturn("12345678");

        MessageResponseDto response = passwordRecoveryService.sendRecoveryCode(new SendEmailRequestDto("qa@example.com"));

        verify(events).publishEvent(any(EmailRequestedEvent.class));
        assertThat(response.message()).isEqualTo(GENERIC_SENT);
    }

    @Test
    void pedidoDeRecuperacaoParaEmailInexistenteRespondeIgualSemEnviar() {
        when(userRepository.findByEmail("ninguem@example.com")).thenReturn(Optional.empty());

        MessageResponseDto response = passwordRecoveryService.sendRecoveryCode(new SendEmailRequestDto("ninguem@example.com"));

        assertThat(response.message()).isEqualTo(GENERIC_SENT);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void redefinirSenhaDeEmailInexistenteLancaCodigoInvalidoSemConsumirCodigo() {
        when(userRepository.findByEmail("ninguem@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordRecoveryService.resetPassword(
                new ResetPasswordRequestDto("12345678", "ninguem@example.com", "senha-nova-123")))
                .isInstanceOf(InvalidVerificationCodeException.class);

        verify(verificationCodeService, never()).consumeCode(anyString(), anyString(), any());
    }

    @Test
    void redefinirSenhaTrocaASenhaRevogaTokensELimpaTentativas() {
        int version = user.getTokenVersion();
        when(userRepository.findByEmail("qa@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("senha-nova-123")).thenReturn("hash-novo");

        MessageResponseDto response = passwordRecoveryService.resetPassword(
                new ResetPasswordRequestDto("12345678", "qa@example.com", "senha-nova-123"));

        verify(verificationCodeService).consumeCode("12345678", "qa@example.com", CodePurpose.PASSWORD_RECOVERY);
        assertThat(user.getPassword()).isEqualTo("hash-novo");
        assertThat(user.getTokenVersion()).isEqualTo(version + 1);
        verify(userRepository).saveAndFlush(user);
        verify(attemptGuard).clearCodeAttempts("qa@example.com", null);
        verify(attemptGuard).clearLogin("qa@example.com");
        assertThat(response.message()).isEqualTo("Senha atualizada com sucesso");
    }

    @Test
    void contaGoogleNaoTemSenhaParaAlterar() {
        user.setAuthProvider(AuthProvider.GOOGLE);
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> passwordRecoveryService.changePassword(user,
                new ChangePasswordRequestDto("qualquer", "senha-nova-123")))
                .isInstanceOf(GoogleAccountWithoutPasswordException.class);
    }

    @Test
    void alterarSenhaComSenhaAtualErradaLancaIncorrectPassword() {
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha-errada", "hash-antigo")).thenReturn(false);

        assertThatThrownBy(() -> passwordRecoveryService.changePassword(user,
                new ChangePasswordRequestDto("senha-errada", "senha-nova-123")))
                .isInstanceOf(IncorrectPasswordException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void alterarSenhaGravaNovaSenhaRevogaTokensEDevolveTokenNovo() {
        int version = user.getTokenVersion();
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
        UserResponseDto mapped = new UserResponseDto(user.getUserId(), "QA", "qa@example.com", null, AuthProvider.LOCAL, true);
        when(passwordEncoder.matches("senha-atual", "hash-antigo")).thenReturn(true);
        when(passwordEncoder.encode("senha-nova-123")).thenReturn("hash-novo");
        when(tokenService.generateToken(user)).thenReturn("token-novo");
        when(userMapper.toResponse(user)).thenReturn(mapped);

        AuthResponseDto response = passwordRecoveryService.changePassword(user,
                new ChangePasswordRequestDto("senha-atual", "senha-nova-123"));

        assertThat(user.getPassword()).isEqualTo("hash-novo");
        assertThat(user.getTokenVersion()).isEqualTo(version + 1);
        assertThat(response.accessToken()).isEqualTo("token-novo");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user()).isEqualTo(mapped);
    }
}
