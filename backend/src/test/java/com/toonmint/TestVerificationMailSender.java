package com.toonmint;

import com.toonmint.domain.VerificationPurpose;
import com.toonmint.service.VerificationMailSender;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Înlocuiește livrarea SMTP în teste și reține ultimul cod emis per email și scop.
 */
@Primary
@Component
public class TestVerificationMailSender implements VerificationMailSender {

    private final Map<String, String> codes = new ConcurrentHashMap<>();

    @Override
    public void send(String email, String code, VerificationPurpose purpose, Instant expiresAt) {
        codes.put(key(email, purpose), code);
    }

    /**
     * Ultimul cod de verificare a emailului trimis către adresa dată.
     */
    public String code(String email) {
        return code(email, VerificationPurpose.EMAIL_VERIFICATION);
    }

    /**
     * Ultimul cod trimis către adresa dată pentru scopul cerut.
     */
    public String code(String email, VerificationPurpose purpose) {
        String code = codes.get(key(email, purpose));
        if (code == null) {
            throw new AssertionError("No " + purpose + " code was sent to " + email);
        }
        return code;
    }

    /**
     * Indică dacă a fost trimis vreun cod către adresa dată.
     */
    public boolean hasCode(String email, VerificationPurpose purpose) {
        return codes.containsKey(key(email, purpose));
    }

    private static String key(String email, VerificationPurpose purpose) {
        return purpose + ":" + email.toLowerCase(Locale.ROOT);
    }
}
