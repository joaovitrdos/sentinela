package br.com.sentinela.service;

import br.com.sentinela.exception.GoogleAuthUnavailableException;
import br.com.sentinela.exception.InvalidGoogleTokenException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleTokenVerifierTest {

    private final GoogleTokenVerifier verifier = new GoogleTokenVerifier("cliente.apps.googleusercontent.com");

    // Regressao: token que nao e JWT respondia 503 (Google indisponivel) em vez de 401
    @Test
    void tokenMalformadoEInvalidoENaoIndisponibilidade() {
        assertThatThrownBy(() -> verifier.verify("isto-nao-e-um-jwt"))
                .isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void tokenVazioEInvalido() {
        assertThatThrownBy(() -> verifier.verify(" "))
                .isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void semClientIdOLoginComGoogleFicaIndisponivel() {
        assertThatThrownBy(() -> new GoogleTokenVerifier("").verify("qualquer.token.aqui"))
                .isInstanceOf(GoogleAuthUnavailableException.class);
    }
}
