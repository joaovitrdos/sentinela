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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailRepository emailRepository;
    @Mock private PropertyRepository propertyRepository;
    @Mock private PropertyActivityRepository propertyActivityRepository;
    @Mock private PlotRepository plotRepository;
    @Mock private FieldActionRepository fieldActionRepository;
    @Mock private PropertyMemberRepository propertyMemberRepository;
    @Mock private UserMapper userMapper;
    @Mock private VerificationCodeService verificationCodeService;
    @Mock private EmailFactory emailFactory;
    @Mock private ApplicationEventPublisher events;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AttemptGuard attemptGuard;
    @Mock private TokenService tokenService;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .userId(UUID.randomUUID())
                .name("Usuário")
                .email("qa@example.com")
                .password("hash")
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(true)
                .build();
    }

    @Test
    void perfilDeUsuarioInexistenteLancaUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findPublicById(userId, userId)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void perfilPorIdDeOutroUsuarioNaoConsultaNemRetornaSeusDados() {
        UUID otherUserId = UUID.randomUUID();

        assertThatThrownBy(() -> userService.findPublicById(user.getUserId(), otherUserId))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(userRepository, userMapper);
    }

    @Test
    void perfilPorIdSemSolicitanteNaoConsultaDados() {
        assertThatThrownBy(() -> userService.findPublicById(null, user.getUserId()))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(userRepository, userMapper);
    }

    @Test
    void perfilPorIdDoProprioUsuarioRetornaDtoLimitado() {
        PublicUserResponseDto mapped = new PublicUserResponseDto(user.getUserId(), user.getName(), null);
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
        when(userMapper.toPublicResponse(user)).thenReturn(mapped);

        PublicUserResponseDto response = userService.findPublicById(user.getUserId(), user.getUserId());

        assertThat(response).isSameAs(mapped);
    }

    @Test
    void atualizarPerfilLimpaEspacosEGravaFotoVaziaComoNula() {
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        userService.updateProfile(user.getUserId(), new UpdateUserRequestDto("  Nome Novo  ", "   "));

        assertThat(user.getName()).isEqualTo("Nome Novo");
        assertThat(user.getPicture()).isNull();
    }

    @Test
    void contaGoogleNaoPodeTrocarEmail() {
        user.setAuthProvider(AuthProvider.GOOGLE);
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.requestEmailChange(user,
                new ChangeEmailRequestDto("novo@example.com", "senha")))
                .isInstanceOf(GoogleAccountEmailChangeException.class);
    }

    @Test
    void trocaDeEmailComSenhaErradaLancaIncorrectPassword() {
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha-errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> userService.requestEmailChange(user,
                new ChangeEmailRequestDto("novo@example.com", "senha-errada")))
                .isInstanceOf(IncorrectPasswordException.class);

        verify(verificationCodeService, never()).createCode(any(), any(), any());
    }

    @Test
    void trocaDeEmailEnviaCodigoParaOEmailNovo() {
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha-certa", "hash")).thenReturn(true);
        when(userRepository.existsByEmail("novo@example.com")).thenReturn(false);
        when(verificationCodeService.createCode(user, CodePurpose.EMAIL_CHANGE, "novo@example.com")).thenReturn("12345678");

        MessageResponseDto response = userService.requestEmailChange(user,
                new ChangeEmailRequestDto(" NOVO@example.com ", "senha-certa"));

        verify(emailFactory).createEmailChange("novo@example.com", "12345678");
        verify(events).publishEvent(any(EmailRequestedEvent.class));
        assertThat(response.message()).isEqualTo("Enviamos um código de confirmação para o novo endereço");
    }

    @Test
    void trocaDeEmailParaEmailJaUsadoNaoRevelaQueEleExiste() {
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha-certa", "hash")).thenReturn(true);
        when(userRepository.existsByEmail("ocupado@example.com")).thenReturn(true);

        MessageResponseDto response = userService.requestEmailChange(user,
                new ChangeEmailRequestDto("ocupado@example.com", "senha-certa"));

        assertThat(response.message()).isEqualTo("Enviamos um código de confirmação para o novo endereço");
        verify(events, never()).publishEvent(any());
    }

    @Test
    void confirmarTrocaDeEmailAtualizaEmailRevogaTokensEDevolveTokenNovo() {
        int version = user.getTokenVersion();
        VerificationCode code = VerificationCode.builder().targetEmail("novo@example.com").build();
        UserResponseDto mapped = new UserResponseDto(user.getUserId(), "QA", "novo@example.com", null, AuthProvider.LOCAL, true);
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
        when(verificationCodeService.consumeCode("12345678", "qa@example.com", CodePurpose.EMAIL_CHANGE)).thenReturn(code);
        when(userRepository.existsByEmail("novo@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(tokenService.generateToken(user)).thenReturn("token-novo");
        when(userMapper.toResponse(user)).thenReturn(mapped);

        AuthResponseDto response = userService.confirmEmailChange(user, new ConfirmEmailChangeRequestDto("12345678"));

        assertThat(user.getEmail()).isEqualTo("novo@example.com");
        assertThat(user.getTokenVersion()).isEqualTo(version + 1);
        assertThat(response.accessToken()).isEqualTo("token-novo");
        assertThat(response.user().email()).isEqualTo("novo@example.com");
    }

    @Test
    void confirmarTrocaParaEmailQueFicouOcupadoLancaEmailAlreadyRegistered() {
        VerificationCode code = VerificationCode.builder().targetEmail("novo@example.com").build();
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
        when(verificationCodeService.consumeCode("12345678", "qa@example.com", CodePurpose.EMAIL_CHANGE)).thenReturn(code);
        when(userRepository.existsByEmail("novo@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.confirmEmailChange(user, new ConfirmEmailChangeRequestDto("12345678")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        assertThat(user.getEmail()).isEqualTo("qa@example.com");
    }

    @Test
    void emailDuplicadoNoMomentoDeSalvarViraEmailAlreadyRegistered() {
        VerificationCode code = VerificationCode.builder().targetEmail("novo@example.com").build();
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
        when(verificationCodeService.consumeCode("12345678", "qa@example.com", CodePurpose.EMAIL_CHANGE)).thenReturn(code);
        when(userRepository.existsByEmail("novo@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(user)).thenThrow(new DataIntegrityViolationException("email duplicado"));

        assertThatThrownBy(() -> userService.confirmEmailChange(user, new ConfirmEmailChangeRequestDto("12345678")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void excluirContaApagaEmailsEUsuario() {
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha", "hash")).thenReturn(true);

        userService.delete(user.getUserId(), new DeleteAccountRequestDto("senha", null));

        verify(emailRepository).deleteByUserId(user.getUserId());
        verify(propertyRepository).deleteAllOwnedBy(user.getUserId());
        verify(userRepository).delete(user);
        verify(userRepository).flush();
    }

    @Test
    void falhaDoBancoAoExcluirViraUserDeleteException() {
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("senha", "hash")).thenReturn(true);
        doThrow(new DataAccessResourceFailureException("banco fora")).when(userRepository).flush();

        assertThatThrownBy(() -> userService.delete(user.getUserId(), new DeleteAccountRequestDto("senha", null)))
                .isInstanceOf(UserDeleteException.class);
    }
}
