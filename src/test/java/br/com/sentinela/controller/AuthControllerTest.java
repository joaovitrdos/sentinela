package br.com.sentinela.controller;

import br.com.sentinela.dto.response.AuthResponseDto;
import br.com.sentinela.dto.response.MessageResponseDto;
import br.com.sentinela.dto.response.UserResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.enums.AuthProvider;
import br.com.sentinela.exception.EmailAlreadyRegisteredException;
import br.com.sentinela.exception.EmailNotVerifiedException;
import br.com.sentinela.exception.GoogleAuthUnavailableException;
import br.com.sentinela.exception.InvalidCredentialsException;
import br.com.sentinela.exception.InvalidVerificationCodeException;
import br.com.sentinela.exception.TooManyLoginAttemptsException;
import br.com.sentinela.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends WebLayerTestSupport {

    private static final String GENERIC_REGISTER =
            "Se este e-mail estiver disponível, enviaremos um código de confirmação para ele";

    @Test
    void cadastroRespondeAcceptedComMensagemGenerica() throws Exception {
        when(authService.register(any())).thenReturn(new MessageResponseDto(GENERIC_REGISTER));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Usuário", "email": "qa@example.com", "password": "senha-segura-1"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(GENERIC_REGISTER));
    }

    @Test
    void cadastroComEmailJaCadastradoRespondeConflict() throws Exception {
        when(authService.register(any())).thenThrow(new EmailAlreadyRegisteredException());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Usuário", "email": "qa@example.com", "password": "senha-segura-1"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().string("Este e-mail já está em uso. Tente outro e-mail"));
    }

    @Test
    void cadastroComDadosInvalidosRespondeBadRequestComAsMensagensDeValidacao() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "email": "nao-e-email", "password": "123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("O nome é obrigatório")))
                .andExpect(content().string(containsString("E-mail inválido")))
                .andExpect(content().string(containsString("A senha deve ter entre 8 e 72 caracteres")));

        verify(authService, never()).register(any());
    }

    @Test
    void corpoMalformadoRespondeBadRequest() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ isto nao e json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Corpo da requisição ausente ou malformado"));
    }

    @Test
    void loginRetornaTokenBearer() throws Exception {
        UserResponseDto user = new UserResponseDto(null, "Usuário", "qa@example.com", null, AuthProvider.LOCAL, true);
        when(authService.login(any())).thenReturn(AuthResponseDto.bearer("jwt-de-teste", user));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "qa@example.com", "password": "senha-segura-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-de-teste"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("qa@example.com"));
    }

    @Test
    void loginComCredenciaisInvalidasRespondeUnauthorized() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "qa@example.com", "password": "senha-errada"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("E-mail ou senha inválidos"));
    }

    @Test
    void loginComEmailNaoConfirmadoRespondeForbidden() throws Exception {
        when(authService.login(any())).thenThrow(new EmailNotVerifiedException());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "qa@example.com", "password": "senha-segura-1"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Confirme seu e-mail")));
    }

    @Test
    void loginBloqueadoPorTentativasRespondeTooManyRequests() throws Exception {
        when(authService.login(any())).thenThrow(new TooManyLoginAttemptsException());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "qa@example.com", "password": "senha-errada"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(containsString("Muitas tentativas de login")));
    }

    @Test
    void confirmarEmailComCodigoInvalidoRespondeBadRequest() throws Exception {
        when(authService.confirmEmail(any())).thenThrow(new InvalidVerificationCodeException());

        mockMvc.perform(post("/auth/email/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "qa@example.com", "code": "00000000"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Código de verificação inválido"));
    }

    @Test
    void confirmarEmailComCodigoMalFormatadoRespondeBadRequest() throws Exception {
        mockMvc.perform(post("/auth/email/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "qa@example.com", "code": "12ab"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("O código deve ter 8 dígitos")));
    }

    @Test
    void loginComGoogleIndisponivelRespondeServiceUnavailable() throws Exception {
        when(authService.loginWithGoogle(any())).thenThrow(new GoogleAuthUnavailableException());

        mockMvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idToken": "token-qualquer"}
                                """))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void logoutEhPublicoERespondeNoContent() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());
    }

    // Regressao: sem token, /auth/logout-all respondia 500 (NullPointerException no AuthService).
    @Test
    void logoutEmTodosOsDispositivosSemTokenRespondeUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/logout-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(UNAUTHORIZED));

        verify(authService, never()).logoutEverywhere(any());
    }

    @Test
    void logoutEmTodosOsDispositivosComTokenRespondeNoContent() throws Exception {
        User user = user();

        mockMvc.perform(post("/auth/logout-all").header("Authorization", bearer(user)))
                .andExpect(status().isNoContent());

        verify(authService).logoutEverywhere(user);
    }

    @Test
    void erroInesperadoRespondeInternalServerErrorSemDetalhesInternos() throws Exception {
        doThrow(new IllegalStateException("detalhe interno")).when(authService).logout();

        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Erro inesperado ao processar a requisição"));
    }
}
