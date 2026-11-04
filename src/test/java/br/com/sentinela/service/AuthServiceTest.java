package br.com.sentinela.service;

import br.com.sentinela.dto.request.LoginRequestDto;
import br.com.sentinela.dto.request.RegisterRequestDto;
import br.com.sentinela.dto.request.SendEmailRequestDto;
import br.com.sentinela.dto.request.ValidateCodeRequestDto;
import br.com.sentinela.dto.response.AuthResponseDto;
import br.com.sentinela.dto.response.MessageResponseDto;
import br.com.sentinela.dto.response.UserResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.enums.AuthProvider;
import br.com.sentinela.enums.CodePurpose;
import br.com.sentinela.exception.EmailAlreadyRegisteredException;
import br.com.sentinela.exception.EmailNotVerifiedException;
import br.com.sentinela.exception.InvalidCredentialsException;
import br.com.sentinela.exception.UserSaveException;
import br.com.sentinela.infra.security.AttemptGuard;
import br.com.sentinela.infra.security.TokenService;
import br.com.sentinela.mapper.UserMapper;
import br.com.sentinela.notification.EmailFactory;
import br.com.sentinela.notification.EmailRequestedEvent;
import br.com.sentinela.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
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
class AuthServiceTest {

    private static final String GENERIC_REGISTER =
            "Se este e-mail estiver disponível, enviaremos um código de confirmação para ele";
    private static final String REGISTER_MESSAGE = "Enviamos um código de confirmação para o seu e-mail";

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private GoogleTokenVerifier googleTokenVerifier;
    @Mock private EmailFactory emailFactory;
    @Mock private ApplicationEventPublisher events;
    @Mock private UserMapper userMapper;
    @Mock private TokenService tokenService;
    @Mock private AttemptGuard attemptGuard;
    @Mock private VerificationCodeService verificationCodeService;

    @InjectMocks
    private AuthService authService;

    private User user(boolean emailVerified) {
        return User.builder()
                .userId(UUID.randomUUID())
                .name("Usuário")
                .email("qa@example.com")
                .password("hash")
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(emailVerified)
                .build();
    }

    @Test
    void cadastroCriaUsuarioComSenhaCriptografadaEEnviaCodigo() {
        when(userRepository.findByEmail("qa@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha-segura-1")).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(verificationCodeService.createCode(any(User.class), any(CodePurpose.class))).thenReturn("12345678");

        MessageResponseDto response = authService.register(
                new RegisterRequestDto("  Usuário  ", "  QA@Example.com ", "senha-segura-1"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("qa@example.com");
        assertThat(saved.getValue().getName()).isEqualTo("Usuário");
        assertThat(saved.getValue().getPassword()).isEqualTo("hash");
        assertThat(saved.getValue().isEmailVerified()).isFalse();
        verify(verificationCodeService).createCode(saved.getValue(), CodePurpose.EMAIL_VERIFICATION);
        verify(events).publishEvent(any(EmailRequestedEvent.class));
        assertThat(response.message()).isEqualTo(REGISTER_MESSAGE);
    }

    @Test
    void cadastroDeEmailJaConfirmadoRecusaComEmailEmUso() {
        when(userRepository.findByEmail("qa@example.com")).thenReturn(Optional.of(user(true)));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequestDto("Usuário", "QA@example.com", "senha-segura-1")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(userRepository, never()).saveAndFlush(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void cadastroDeEmailDeContaGoogleRecusaComEmailEmUso() {
        User google = User.builder()
                .userId(UUID.randomUUID())
                .email("qa@example.com")
                .googleId("google-1")
                .authProvider(AuthProvider.GOOGLE)
                .emailVerified(false)
                .build();
        when(userRepository.findByEmail("qa@example.com")).thenReturn(Optional.of(google));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequestDto("Usuário", "qa@example.com", "senha-segura-1")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(events, never()).publishEvent(any());
    }

    @Test
    void cadastroDeEmailAindaNaoConfirmadoReenviaOCodigo() {
        User pending = user(false);
        when(userRepository.findByEmail("qa@example.com")).thenReturn(Optional.of(pending));
        when(verificationCodeService.createCode(pending, CodePurpose.EMAIL_VERIFICATION)).thenReturn("12345678");

        MessageResponseDto response = authService.register(
                new RegisterRequestDto("Usuário", "qa@example.com", "senha-segura-1"));

        assertThat(response.message()).isEqualTo(REGISTER_MESSAGE);
        verify(userRepository, never()).saveAndFlush(any());
        verify(events).publishEvent(any(EmailRequestedEvent.class));
    }

    @Test
    void cadastroConcorrenteComEmailDuplicadoRecusaComEmailEmUso() {
        when(userRepository.findByEmail("qa@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("email duplicado"));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequestDto("Usuário", "qa@example.com", "senha-segura-1")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(events, never()).publishEvent(any());
    }

    @Test
    void falhaDoBancoNoCadastroViraUserSaveException() {
        when(userRepository.findByEmail("qa@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataAccessResourceFailureException("banco fora"));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequestDto("Usuário", "qa@example.com", "senha-segura-1")))
                .isInstanceOf(UserSaveException.class);
    }

    // Regressao: o reenvio respondia "E-mail ou senha inválidos".
    @Test
    void reenvioDeCodigoRespondeAMesmaMensagemGenericaDoCadastro() {
        User pending = user(false);
        when(userRepository.findByEmail("qa@example.com")).thenReturn(Optional.of(pending));
        when(verificationCodeService.createCode(pending, CodePurpose.EMAIL_VERIFICATION)).thenReturn("12345678");

        MessageResponseDto response = authService.resendVerification(new SendEmailRequestDto("qa@example.com"));

        assertThat(response.message()).isEqualTo(GENERIC_REGISTER);
        verify(events).publishEvent(any(EmailRequestedEvent.class));
    }

    @Test
    void reenvioParaEmailInexistenteNaoEnviaNadaMasRespondeIgual() {
        when(userRepository.findByEmail("ninguem@example.com")).thenReturn(Optional.empty());

        MessageResponseDto response = authService.resendVerification(new SendEmailRequestDto("ninguem@example.com"));

        assertThat(response.message()).isEqualTo(GENERIC_REGISTER);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void loginComSenhaErradaRegistraFalhaELancaInvalidCredentials() {
        when(userRepository.findByEmail("qa@example.com")).thenReturn(Optional.of(user(true)));
        when(passwordEncoder.matches("senha-errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequestDto("qa@example.com", "senha-errada")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(attemptGuard).checkLogin("qa@example.com");
        verify(attemptGuard).recordLoginFailure("qa@example.com");
        verify(tokenService, never()).generateToken(any());
    }

    @Test
    void loginDeUsuarioInexistenteLancaAMesmaExcecaoDaSenhaErrada() {
        when(userRepository.findByEmail("ninguem@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequestDto("ninguem@example.com", "senha-qualquer")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(attemptGuard).recordLoginFailure("ninguem@example.com");
    }

    @Test
    void loginComEmailNaoConfirmadoLancaEmailNotVerified() {
        when(userRepository.findByEmail("qa@example.com")).thenReturn(Optional.of(user(false)));
        when(passwordEncoder.matches("senha-certa", "hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequestDto("qa@example.com", "senha-certa")))
                .isInstanceOf(EmailNotVerifiedException.class);

        verify(tokenService, never()).generateToken(any());
    }

    @Test
    void loginValidoLimpaTentativasERetornaToken() {
        User user = user(true);
        UserResponseDto dto = new UserResponseDto(user.getUserId(), "Usuário", "qa@example.com", null, AuthProvider.LOCAL, true);
        when(userRepository.findByEmail("qa@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha-certa", "hash")).thenReturn(true);
        when(tokenService.generateToken(user)).thenReturn("jwt");
        when(userMapper.toResponse(user)).thenReturn(dto);

        AuthResponseDto response = authService.login(new LoginRequestDto(" QA@example.com ", "senha-certa"));

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user()).isEqualTo(dto);
        verify(attemptGuard).clearLogin("qa@example.com");
    }

    @Test
    void confirmarEmailMarcaComoVerificadoEDevolveToken() {
        User user = user(false);
        when(userRepository.findByEmail("qa@example.com")).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(tokenService.generateToken(user)).thenReturn("jwt");

        AuthResponseDto response = authService.confirmEmail(new ValidateCodeRequestDto("12345678", "qa@example.com"));

        verify(verificationCodeService).consumeCode("12345678", "qa@example.com", CodePurpose.EMAIL_VERIFICATION);
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(response.accessToken()).isEqualTo("jwt");
        verify(attemptGuard).clearCodeAttempts("qa@example.com");
    }

    @Test
    void logoutEmTodosOsDispositivosRevogaOsTokens() {
        User user = user(true);
        int version = user.getTokenVersion();

        authService.logoutEverywhere(user);

        assertThat(user.getTokenVersion()).isEqualTo(version + 1);
        verify(userRepository).saveAndFlush(user);
    }
}
