package com.toonmint.security;

import com.toonmint.config.AppProperties;
import com.toonmint.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    /**
     * Validează secretul configurat și pregătește cheia HMAC.
     */
    public JwtService(AppProperties properties) {
        byte[] secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes long");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.ttl = properties.jwt().ttl();
    }

    /**
     * Generează un token JWT semnat pentru utilizatorul dat.
     */
    public Token issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        String value = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new Token(value, expiresAt);
    }

    /**
     * Validează tokenul și extrage identitatea; întoarce empty dacă tokenul e invalid sau expirat.
     */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthenticatedUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    com.toonmint.domain.UserRole.valueOf(claims.get("role", String.class))
            ));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * Tokenul emis și momentul expirării lui.
     */
    public record Token(String value, Instant expiresAt) {
    }
}
