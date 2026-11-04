package br.com.sentinela.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import br.com.sentinela.entity.User;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class TokenService {

    private static final String ISSUER = "sentinela";
    private static final String VERSION_CLAIM = "ver";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Duration TOKEN_TTL = Duration.ofDays(1);
    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${api.security.token.secret}")
    private String secret;

    @PostConstruct
    void requireStrongSecret() {
        if (secret == null || secret.strip().length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "SENTINELA_JWT_SECRET precisa de ao menos " + MIN_SECRET_LENGTH
                            + " caracteres. Gere um com: openssl rand -base64 48");
        }
    }

    public String generateToken(User user) {
        try {
            return JWT.create()
                    .withIssuer(ISSUER)
                    .withSubject(user.getUserId().toString())
                    .withClaim(VERSION_CLAIM, user.getTokenVersion())
                    .withExpiresAt(genExpirationDate())
                    .sign(Algorithm.HMAC256(secret));
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Erro ao gerar o token", exception);
        }
    }

    public Credentials credentialsFrom(String authorizationHeader) {
        DecodedJWT decoded = decode(authorizationHeader);

        if (decoded == null) {
            return null;
        }

        Claim version = decoded.getClaim(VERSION_CLAIM);
        UUID userId = parseUserId(decoded.getSubject());
        if (userId == null) {
            return null;
        }
        return new Credentials(userId, version.isMissing() || version.isNull()
                ? null
                : version.asInt());
    }

    /** Chave estavel do usuario (o userId), usada para o rate limit por usuario. */
    public String subjectFrom(String authorizationHeader) {
        Credentials credentials = credentialsFrom(authorizationHeader);
        return credentials == null ? null : credentials.userId().toString();
    }

    // O subject e o userId (imutavel). Tokens antigos, emitidos com o e-mail no subject, deixam de valer.
    private static UUID parseUserId(String subject) {
        if (subject == null) {
            return null;
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private DecodedJWT decode(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }
        try {
            return JWT.require(Algorithm.HMAC256(secret))
                    .withIssuer(ISSUER)
                    .build()
                    .verify(authorizationHeader.replace(BEARER_PREFIX, ""));
        } catch (JWTVerificationException exception) {
            return null;
        }
    }

    private Instant genExpirationDate() {
        return Instant.now().plus(TOKEN_TTL);
    }

    public record Credentials(UUID userId, Integer tokenVersion) {
        public boolean matches(User user) {
            return tokenVersion != null && tokenVersion == user.getTokenVersion()
                    && userId.equals(user.getUserId());
        }
    }
}
