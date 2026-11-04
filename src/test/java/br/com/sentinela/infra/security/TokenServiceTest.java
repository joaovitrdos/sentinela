package br.com.sentinela.infra.security;

import br.com.sentinela.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private static final String SECRET = "segredo-de-teste-com-mais-de-32-caracteres-123";

    private TokenService tokenService;
    private User user;

    @BeforeEach
    void setUp() {
        tokenService = tokenServiceWith(SECRET);
        user = User.builder().email("qa@example.com").build();
    }

    private TokenService tokenServiceWith(String secret) {
        TokenService service = new TokenService();
        ReflectionTestUtils.setField(service, "secret", secret);
        return service;
    }

    @Test
    void tokenGeradoDevolveOEmailEAVersaoDoUsuario() {
        String token = tokenService.generateToken(user);

        TokenService.Credentials credentials = tokenService.credentialsFrom("Bearer " + token);

        assertThat(credentials.email()).isEqualTo("qa@example.com");
        assertThat(credentials.tokenVersion()).isEqualTo(user.getTokenVersion());
        assertThat(credentials.matches(user)).isTrue();
    }

    @Test
    void tokenDeixaDeValerQuandoOsTokensDoUsuarioSaoRevogados() {
        String token = tokenService.generateToken(user);

        user.revokeIssuedTokens();

        assertThat(tokenService.credentialsFrom("Bearer " + token).matches(user)).isFalse();
    }

    @Test
    void tokenAlteradoOuDeOutroSegredoEhRecusado() {
        String token = tokenService.generateToken(user);
        String fromOtherSecret = tokenServiceWith("outro-segredo-com-mais-de-32-caracteres-xyz").generateToken(user);

        assertThat(tokenService.credentialsFrom("Bearer " + token + "x")).isNull();
        assertThat(tokenService.credentialsFrom("Bearer " + fromOtherSecret)).isNull();
        assertThat(tokenService.credentialsFrom("Bearer nao-e-um-jwt")).isNull();
    }

    @Test
    void semCabecalhoNaoHaCredenciais() {
        assertThat(tokenService.credentialsFrom(null)).isNull();
        assertThat(tokenService.emailFrom(null)).isNull();
    }

    @Test
    void segredoCurtoImpedeAAplicacaoDeSubir() {
        TokenService weak = tokenServiceWith("curto");

        assertThatThrownBy(weak::requireStrongSecret)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SENTINELA_JWT_SECRET");
    }
}
