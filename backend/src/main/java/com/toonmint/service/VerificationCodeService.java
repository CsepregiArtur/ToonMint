package com.toonmint.service;

import com.toonmint.domain.User;
import com.toonmint.domain.VerificationCode;
import com.toonmint.domain.VerificationPurpose;
import com.toonmint.repository.VerificationCodeRepository;
import com.toonmint.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Emite și validează codurile OTP de 6 cifre folosite la verificarea emailului și
 * la resetarea parolei. Codurile sunt stocate doar ca hash BCrypt, expiră după
 * 15 minute și acceptă maximum 5 încercări greșite.
 */
@Service
public class VerificationCodeService {

    public static final Duration CODE_TTL = Duration.ofMinutes(15);
    public static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    public static final int MAX_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationCodeRepository codes;
    private final PasswordEncoder passwordEncoder;

    /**
     * Injectează repository-ul de coduri și encoder-ul folosit pentru hash.
     */
    public VerificationCodeService(VerificationCodeRepository codes, PasswordEncoder passwordEncoder) {
        this.codes = codes;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Generează un cod nou, invalidează codurile anterioare active și îl persistă.
     * Respinge cererea dacă ultimul cod a fost emis cu mai puțin de 60 de secunde în urmă.
     */
    @Transactional
    public IssuedCode issue(User user, VerificationPurpose purpose) {
        Instant now = Instant.now();
        codes.findTopByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(), purpose)
                .filter(last -> last.getCreatedAt().isAfter(now.minus(RESEND_COOLDOWN)))
                .ifPresent(last -> {
                    long retryAfter = RESEND_COOLDOWN.minus(Duration.between(last.getCreatedAt(), now)).toSeconds();
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CODE_COOLDOWN",
                            "A code was sent moments ago. Please wait before requesting another one",
                            Map.of("retryAfterSeconds", Math.max(1, retryAfter)));
                });

        codes.consumeActive(user.getId(), purpose, now);
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        VerificationCode entity = new VerificationCode(user.getId(), purpose,
                passwordEncoder.encode(code), now.plus(CODE_TTL));
        codes.save(entity);
        return new IssuedCode(code, entity.getExpiresAt());
    }

    /**
     * Validează codul trimis de utilizator. Consumă codul la succes; la eșec
     * incrementează contorul de încercări. Rulează într-o tranzacție separată
     * pentru ca încercările eșuate să fie păstrate chiar dacă apelantul aruncă eroare.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean verify(User user, VerificationPurpose purpose, String submittedCode) {
        Instant now = Instant.now();
        VerificationCode code = codes
                .findTopByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(user.getId(), purpose)
                .orElse(null);
        if (code == null) {
            return false;
        }
        if (code.isExpired(now) || code.isExhausted(MAX_ATTEMPTS)) {
            code.markConsumed(now);
            codes.save(code);
            return false;
        }
        if (!passwordEncoder.matches(normalize(submittedCode), code.getCodeHash())) {
            code.registerFailedAttempt();
            if (code.isExhausted(MAX_ATTEMPTS)) {
                code.markConsumed(now);
            }
            codes.save(code);
            return false;
        }
        code.markConsumed(now);
        codes.save(code);
        return true;
    }

    /**
     * Normalizează codul primit (spații din copierea emailului).
     */
    private static String normalize(String code) {
        return code == null ? "" : code.trim();
    }

    /**
     * Codul emis și momentul expirării lui.
     */
    public record IssuedCode(String code, Instant expiresAt) {
    }
}
