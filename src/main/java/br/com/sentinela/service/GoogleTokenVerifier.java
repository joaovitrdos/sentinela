package br.com.sentinela.service;

import br.com.sentinela.exception.GoogleAuthUnavailableException;
import br.com.sentinela.exception.InvalidGoogleTokenException;
import br.com.sentinela.util.LogUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;
    private final boolean configured;

    public GoogleTokenVerifier(@Value("${sentinela.google.client-id:}") String clientId) {
        this.configured = StringUtils.hasText(clientId);
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId))
                .build();

        if (!configured) {
            LogUtil.warn("sentinela.google.client-id não configurado: o login com Google ficará indisponível");
        }
    }

    public GoogleIdToken.Payload verify(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new InvalidGoogleTokenException();
        }
        if (!configured) {
            throw new GoogleAuthUnavailableException();
        }

        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new InvalidGoogleTokenException();
            }
            return token.getPayload();
        } catch (IllegalArgumentException e) {
            // Token que nem e um JWT: erro do cliente, nao indisponibilidade do Google
            throw new InvalidGoogleTokenException();
        } catch (GeneralSecurityException | IOException e) {
            throw new GoogleAuthUnavailableException(e);
        }
    }
}
