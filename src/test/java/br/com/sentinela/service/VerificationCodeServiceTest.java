package br.com.sentinela.service;

import br.com.sentinela.entity.User;
import br.com.sentinela.entity.VerificationCode;
import br.com.sentinela.enums.CodePurpose;
import br.com.sentinela.exception.ExpiredVerificationCodeException;
import br.com.sentinela.exception.InvalidVerificationCodeException;
import br.com.sentinela.repository.VerificationCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationCodeServiceTest {

    /** Encoder previsivel para o teste: o "hash" e o proprio codigo com prefixo. */
    private static final PasswordEncoder FAKE_ENCODER = new PasswordEncoder() {
        @Override
        public String encode(CharSequence raw) {
            return "hash:" + raw;
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
            return ("hash:" + raw).equals(encoded);
        }
    };

    @Mock
    private VerificationCodeRepository repository;

    private VerificationCodeService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new VerificationCodeService(repository, FAKE_ENCODER);
        user = User.builder().email("qa@example.com").build();
    }

    private VerificationCode pendingCode(String code, LocalDateTime expiresAt) {
        return VerificationCode.builder()
                .user(user)
                .purpose(CodePurpose.PASSWORD_RECOVERY)
                .codeHash(FAKE_ENCODER.encode(code))
                .expiresAt(expiresAt)
                .build();
    }

    @Test
    void criarCodigoGeraOitoDigitosEGuardaSoOHash() {
        when(repository.findAllByUserEmailAndPurposeAndUsedFalse("qa@example.com", CodePurpose.EMAIL_VERIFICATION))
                .thenReturn(List.of());

        String code = service.createCode(user, CodePurpose.EMAIL_VERIFICATION);

        ArgumentCaptor<VerificationCode> saved = ArgumentCaptor.forClass(VerificationCode.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(code).matches("\\d{8}");
        assertThat(saved.getValue().getCodeHash()).isEqualTo("hash:" + code).isNotEqualTo(code);
        assertThat(saved.getValue().getPurpose()).isEqualTo(CodePurpose.EMAIL_VERIFICATION);
        assertThat(saved.getValue().getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void criarCodigoInvalidaOsCodigosPendentesAnteriores() {
        VerificationCode old = pendingCode("11111111", LocalDateTime.now().plusMinutes(10));
        when(repository.findAllByUserEmailAndPurposeAndUsedFalse("qa@example.com", CodePurpose.PASSWORD_RECOVERY))
                .thenReturn(List.of(old));

        service.createCode(user, CodePurpose.PASSWORD_RECOVERY);

        assertThat(old.getUsed()).isTrue();
        verify(repository).saveAllAndFlush(List.of(old));
    }

    @Test
    void consumirCodigoValidoMarcaComoUsado() {
        VerificationCode code = pendingCode("12345678", LocalDateTime.now().plusMinutes(10));
        when(repository.findAllByUserEmailAndPurposeAndUsedFalse("qa@example.com", CodePurpose.PASSWORD_RECOVERY))
                .thenReturn(List.of(code));
        when(repository.saveAndFlush(code)).thenReturn(code);

        service.consumeCode("12345678", " QA@example.com ", CodePurpose.PASSWORD_RECOVERY);

        assertThat(code.getUsed()).isTrue();
    }

    @Test
    void validarCodigoNaoConsomeOCodigo() {
        VerificationCode code = pendingCode("12345678", LocalDateTime.now().plusMinutes(10));
        when(repository.findAllByUserEmailAndPurposeAndUsedFalse("qa@example.com", CodePurpose.PASSWORD_RECOVERY))
                .thenReturn(List.of(code));

        service.validateCode("12345678", "qa@example.com", CodePurpose.PASSWORD_RECOVERY);

        assertThat(code.getUsed()).isFalse();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void codigoErradoLancaInvalidVerificationCode() {
        when(repository.findAllByUserEmailAndPurposeAndUsedFalse("qa@example.com", CodePurpose.PASSWORD_RECOVERY))
                .thenReturn(List.of(pendingCode("12345678", LocalDateTime.now().plusMinutes(10))));

        assertThatThrownBy(() -> service.consumeCode("00000000", "qa@example.com", CodePurpose.PASSWORD_RECOVERY))
                .isInstanceOf(InvalidVerificationCodeException.class);
    }

    @Test
    void codigoExpiradoLancaExpiredVerificationCode() {
        when(repository.findAllByUserEmailAndPurposeAndUsedFalse("qa@example.com", CodePurpose.PASSWORD_RECOVERY))
                .thenReturn(List.of(pendingCode("12345678", LocalDateTime.now().minusMinutes(1))));

        assertThatThrownBy(() -> service.consumeCode("12345678", "qa@example.com", CodePurpose.PASSWORD_RECOVERY))
                .isInstanceOf(ExpiredVerificationCodeException.class);
    }
}
