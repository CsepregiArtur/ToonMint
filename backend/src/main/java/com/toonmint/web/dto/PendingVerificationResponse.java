package com.toonmint.web.dto;

/**
 * Răspuns după înregistrare: contul există, dar trebuie confirmat prin codul trimis pe email.
 */
public record PendingVerificationResponse(String email,
                                          boolean verificationRequired,
                                          long codeExpiresInSeconds,
                                          long resendAvailableInSeconds) {
}
