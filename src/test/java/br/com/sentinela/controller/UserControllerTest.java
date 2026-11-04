package br.com.sentinela.controller;

import br.com.sentinela.dto.response.AuthResponseDto;
import br.com.sentinela.dto.response.PublicUserResponseDto;
import br.com.sentinela.dto.response.UserResponseDto;
import br.com.sentinela.entity.User;
import br.com.sentinela.enums.AuthProvider;
import br.com.sentinela.exception.IncorrectPasswordException;
import br.com.sentinela.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest extends WebLayerTestSupport {

    @Test
    void meuPerfilSemTokenRespondeUnauthorized() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(UNAUTHORIZED));

        verifyNoInteractions(userService);
    }

    @Test
    void meuPerfilComTokenInvalidoRespondeUnauthorized() throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meuPerfilComTokenRevogadoRespondeUnauthorized() throws Exception {
        User user = user();
        String token = bearer(user);
        user.revokeIssuedTokens();

        mockMvc.perform(get("/users/me").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(UNAUTHORIZED));
    }

    @Test
    void meuPerfilComTokenValidoRetornaOUsuario() throws Exception {
        User user = user();
        when(userService.findById(user.getUserId())).thenReturn(
                new UserResponseDto(user.getUserId(), user.getName(), user.getEmail(), null, AuthProvider.LOCAL, true));

        mockMvc.perform(get("/users/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("qa@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void perfilPorIdSemTokenRespondeUnauthorized() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/users/{userId}", userId))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(UNAUTHORIZED));

        verifyNoInteractions(userService);
    }

    @Test
    void perfilPorIdDoProprioUsuarioRetornaDadosLimitados() throws Exception {
        User user = user();
        when(userService.findPublicById(user.getUserId(), user.getUserId()))
                .thenReturn(new PublicUserResponseDto(user.getUserId(), "Usuário", null));

        mockMvc.perform(get("/users/{userId}", user.getUserId()).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Usuário"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(userService).findPublicById(user.getUserId(), user.getUserId());
    }

    @Test
    void perfilPorIdDeOutroUsuarioRespondeNotFound() throws Exception {
        User principal = user();
        UUID otherUserId = UUID.randomUUID();
        when(userService.findPublicById(principal.getUserId(), otherUserId)).thenThrow(new UserNotFoundException());

        mockMvc.perform(get("/users/{userId}", otherUserId).header("Authorization", bearer(principal)))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Usuário não encontrado"));

        verify(userService).findPublicById(principal.getUserId(), otherUserId);
    }

    @Test
    void perfilPorIdDoProprioUsuarioInexistenteRespondeNotFound() throws Exception {
        User user = user();
        when(userService.findPublicById(user.getUserId(), user.getUserId())).thenThrow(new UserNotFoundException());

        mockMvc.perform(get("/users/{userId}", user.getUserId()).header("Authorization", bearer(user)))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Usuário não encontrado"));
    }

    @Test
    void perfilPorIdComIdInvalidoRespondeBadRequest() throws Exception {
        mockMvc.perform(get("/users/id-invalido").header("Authorization", bearer(user())))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void totalDeUsuariosSemTokenRespondeUnauthorized() throws Exception {
        mockMvc.perform(get("/users/count"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(UNAUTHORIZED));

        verifyNoInteractions(userService);
    }

    @Test
    void totalDeUsuariosComTokenRetornaContagem() throws Exception {
        when(userService.count()).thenReturn(3L);

        mockMvc.perform(get("/users/count").header("Authorization", bearer(user())))
                .andExpect(status().isOk())
                .andExpect(content().string("3"));
    }

    @Test
    void atualizarPerfilSemTokenRespondeUnauthorized() throws Exception {
        mockMvc.perform(put("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Novo nome"}
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    @Test
    void atualizarPerfilComDadosInvalidosRespondeBadRequest() throws Exception {
        mockMvc.perform(put("/users/me")
                        .header("Authorization", bearer(user()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "picture": "ftp://example.com/foto.png"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("O nome é obrigatório")))
                .andExpect(content().string(containsString("A foto deve ser um endereço http ou https")));
    }

    @Test
    void alterarSenhaComSenhaAtualIncorretaRespondeUnauthorized() throws Exception {
        User user = user();
        when(passwordRecoveryService.changePassword(any(), any())).thenThrow(new IncorrectPasswordException());

        mockMvc.perform(post("/users/change-password")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "senha-errada", "newPassword": "senha-nova-123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("A senha informada está incorreta"));
    }

    @Test
    void alterarSenhaRetornaTokenNovoParaASessaoAtual() throws Exception {
        User user = user();
        UserResponseDto response = new UserResponseDto(user.getUserId(), user.getName(), user.getEmail(), null,
                AuthProvider.LOCAL, true);
        when(passwordRecoveryService.changePassword(any(), any())).thenReturn(AuthResponseDto.bearer("token-novo", response));

        mockMvc.perform(post("/users/change-password")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "senha-atual-123", "newPassword": "senha-nova-123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-novo"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("qa@example.com"));
    }

    @Test
    void confirmarTrocaDeEmailRetornaTokenNovoParaASessaoAtual() throws Exception {
        User user = user();
        UserResponseDto response = new UserResponseDto(user.getUserId(), user.getName(), "novo@example.com", null,
                AuthProvider.LOCAL, true);
        when(userService.confirmEmailChange(any(), any(), any())).thenReturn(AuthResponseDto.bearer("token-novo", response));

        mockMvc.perform(post("/users/me/email/confirm")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "12345678"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-novo"))
                .andExpect(jsonPath("$.user.email").value("novo@example.com"));
    }

    @Test
    void solicitarTrocaDeEmailSemTokenRespondeUnauthorized() throws Exception {
        mockMvc.perform(post("/users/me/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newEmail": "novo@example.com", "currentPassword": "senha-atual-123"}
                                """))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).requestEmailChange(any(), any(), any());
    }

    @Test
    void excluirContaSemTokenRespondeUnauthorized() throws Exception {
        mockMvc.perform(delete("/users/me"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).delete(any(), any());
    }

    @Test
    void excluirContaComTokenRespondeNoContent() throws Exception {
        User user = user();

        mockMvc.perform(delete("/users/me").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"senha\"}"))
                .andExpect(status().isNoContent());

        verify(userService).delete(org.mockito.ArgumentMatchers.eq(user.getUserId()), any());
    }
}
