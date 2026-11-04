package br.com.sentinela.infra.security;

import br.com.sentinela.exception.TooManyCodeAttemptsException;
import br.com.sentinela.exception.TooManyCodeRequestsException;
import br.com.sentinela.exception.TooManyLoginAttemptsException;
import br.com.sentinela.infra.config.SentinelaSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttemptGuardTest {

    private static final String EMAIL = "qa@example.com";

    private AttemptGuard guard;

    @BeforeEach
    void setUp() {
        SentinelaSecurityProperties.Limit limit = new SentinelaSecurityProperties.Limit(100, Duration.ofMinutes(1));
        guard = new AttemptGuard(new SentinelaSecurityProperties(
                new SentinelaSecurityProperties.RateLimit(true, limit, limit, limit),
                new SentinelaSecurityProperties.Attempts(
                        3, Duration.ofMinutes(15),
                        2, Duration.ofHours(1),
                        2, Duration.ofMinutes(15))));
    }

    @Test
    void bloqueiaLoginDepoisDoMaximoDeFalhas() {
        guard.recordLoginFailure(EMAIL);
        guard.recordLoginFailure(EMAIL);
        assertThatCode(() -> guard.checkLogin(EMAIL)).doesNotThrowAnyException();

        guard.recordLoginFailure(EMAIL);

        assertThatThrownBy(() -> guard.checkLogin(EMAIL)).isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void loginComSucessoZeraAsFalhas() {
        guard.recordLoginFailure(EMAIL);
        guard.recordLoginFailure(EMAIL);
        guard.recordLoginFailure(EMAIL);

        guard.clearLogin(EMAIL);

        assertThatCode(() -> guard.checkLogin(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void contadoresSaoSeparadosPorEmail() {
        guard.recordLoginFailure(EMAIL);
        guard.recordLoginFailure(EMAIL);
        guard.recordLoginFailure(EMAIL);

        assertThatCode(() -> guard.checkLogin("outro@example.com")).doesNotThrowAnyException();
    }

    @Test
    void bloqueiaPedidosDeCodigoAcimaDoLimite() {
        guard.checkRecovery(EMAIL);
        guard.checkRecovery(EMAIL);

        assertThatThrownBy(() -> guard.checkRecovery(EMAIL)).isInstanceOf(TooManyCodeRequestsException.class);
    }

    @Test
    void bloqueiaTentativasDeCodigoAcimaDoLimiteAteSeremLimpas() {
        guard.checkCodeAttempt(EMAIL);
        guard.checkCodeAttempt(EMAIL);
        assertThatThrownBy(() -> guard.checkCodeAttempt(EMAIL)).isInstanceOf(TooManyCodeAttemptsException.class);

        guard.clearCodeAttempts(EMAIL);

        assertThatCode(() -> guard.checkCodeAttempt(EMAIL)).doesNotThrowAnyException();
    }
}
