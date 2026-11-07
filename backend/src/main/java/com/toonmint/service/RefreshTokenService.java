package com.toonmint.service;

import com.toonmint.config.AppProperties;
import com.toonmint.domain.RefreshToken;
import com.toonmint.repository.RefreshTokenRepository;
import com.toonmint.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Emite și rotește tokenurile de refresh. JWT-ul are viață scurtă, iar tokenul
 * de refresh ține sesiunea deschisă între lansări — necesar pentru aplicația
 * mobilă, unde un relogin zilnic ar fi inacceptabil.
 *
 * <p>Tokenul este aleator (256 de biți) și se stochează doar ca SHA-256. La
 * fiecare refresh este revocat și înlocuit cu altul; dacă un token deja revocat
 * apare din nou, înseamnă că a fost copiat, deci se închid toate sesiunile
 * utilizatorului.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final Duration ttl;

    /**
     * Leagă repository-ul și durata de viață configurată pentru tokenul de refresh.
     */
    public RefreshTokenService(RefreshTokenRepository repository, AppProperties properties) {
        this.repository = repository;
        this.ttl = properties.jwt().refreshTtl();
    }

    /**
     * Emite un token nou pentru utilizator. Se apelează la fiecare login, deci
     * fiecare dispozitiv are propria sesiune.
     */
    @Transactional
    public Issued issue(Long userId) {
        Instant now = Instant.now();
        repository.deleteExpired(now);
        String value = newToken();
        Instant expiresAt = now.plus(ttl);
        repository.save(new RefreshToken(userId, hash(value), expiresAt));
        return new Issued(value, expiresAt);
    }

    /**
     * Consumă tokenul primit și emite altul în locul lui (rotație).
     *
     * <p>Un token deja consumat nu produce excepție aici: anularea celorlalte
     * sesiuni este o scriere, iar o excepție ar face rollback peste ea. Apelantul
     * primește {@link Rotation#compromised()} și decide răspunsul.
     *
     * @throws ApiException 401 dacă tokenul este necunoscut sau expirat
     */
    @Transactional
    public Rotation rotate(String presentedValue) {
        Instant now = Instant.now();
        RefreshToken stored = repository.findByTokenHash(hash(presentedValue))
                .orElseThrow(RefreshTokenService::invalidToken);
        if (stored.isRevoked()) {
            // Refolosirea unui token consumat înseamnă că amândouă copiile sunt
            // compromise: se închid toate sesiunile contului.
            revokeAll(stored.getUserId());
            return Rotation.compromised(stored.getUserId());
        }
        if (stored.isExpired(now)) {
            throw invalidToken();
        }
        stored.revoke(now);
        return Rotation.renewed(stored.getUserId(), issue(stored.getUserId()));
    }

    /**
     * Anulează tokenul primit (logout). Un token necunoscut nu este o eroare:
     * rezultatul cerut — sesiunea nu mai există — este deja îndeplinit.
     */
    @Transactional
    public void revoke(String presentedValue) {
        repository.findByTokenHash(hash(presentedValue))
                .ifPresent(token -> token.revoke(Instant.now()));
    }

    /**
     * Anulează toate sesiunile unui utilizator (schimbarea parolei, token refolosit).
     * Rulează în propria tranzacție, ca anularea să nu fie pierdută dacă apelantul
     * semnalează apoi eroare și face rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAll(Long userId) {
        repository.revokeAllForUser(userId, Instant.now());
    }

    /**
     * Generează o valoare aleatoare de 256 de biți, sigură pentru URL.
     */
    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Calculează SHA-256 al tokenului, singura formă care ajunge în baza de date.
     */
    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    /**
     * Tokenul emis și momentul expirării lui.
     */
    public record Issued(String value, Instant expiresAt) {
    }

    /**
     * Rezultatul rotației: utilizatorul și tokenul nou, sau absența lui când
     * tokenul primit fusese deja consumat.
     */
    public record Rotation(Long userId, Issued issued) {

        /**
         * Rotație reușită: tokenul vechi a fost consumat, cel nou e valid.
         */
        static Rotation renewed(Long userId, Issued issued) {
            return new Rotation(userId, issued);
        }

        /**
         * Token refolosit: toate sesiunile utilizatorului au fost anulate.
         */
        static Rotation compromised(Long userId) {
            return new Rotation(userId, null);
        }

        /**
         * Indică dacă tokenul primit fusese deja consumat.
         */
        public boolean compromised() {
            return issued == null;
        }
    }

    /**
     * Eroarea comună pentru orice token de refresh neacceptat.
     */
    static ApiException invalidToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                "The session has expired. Sign in again.");
    }
}
