package com.helpdeskpro.backend.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.helpdeskpro.backend.domain.User;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class TokenService {

    @Value("${api.security.token.secret}")
    private String secret;

    private static final String ISSUER = "helpdesk-pro";

    @PostConstruct
    public void validateSecretConfiguration() {
        if (secret == null || secret.trim().length() < 16) {
            throw new IllegalStateException(
                    "A propriedade 'api.security.token.secret' (JWT_SECRET) deve ser configurada com pelo menos 16 caracteres."
            );
        }
    }

    public String generateToken(User user) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer(ISSUER)
                    .withSubject(user.getId().toString())
                    .withClaim("sessionVersion", user.getSessionVersion())
                    .withClaim("id", user.getId())
                    .withClaim("name", user.getName())
                    .withClaim("role", user.getRole().name())
                    .withExpiresAt(Date.from(Instant.now().plus(2, ChronoUnit.HOURS)))
                    .sign(algorithm);
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Erro ao gerar token JWT", exception);
        }
    }

    public record ValidatedSession(long userId, long sessionVersion) {}

    public ValidatedSession validateToken(String token) {
        try {
            var verified = JWT.require(Algorithm.HMAC256(secret))
                    .withIssuer(ISSUER)
                    .build()
                    .verify(token);
            Long id = verified.getClaim("id").asLong();
            Long version = verified.getClaim("sessionVersion").asLong();
            if (id == null || id <= 0 || version == null || version < 0
                    || verified.getExpiresAt() == null
                    || !Long.toString(id).equals(verified.getSubject())) {
                return null;
            }
            return new ValidatedSession(id, version);
        } catch (JWTVerificationException | IllegalArgumentException exception) {
            return null;
        }
    }
}