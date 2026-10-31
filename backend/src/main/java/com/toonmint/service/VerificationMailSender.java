package com.toonmint.service;

import com.toonmint.domain.VerificationPurpose;

import java.time.Instant;

/**
 * Livrarea codurilor OTP către utilizator. Abstractizarea permite înlocuirea
 * livrării în teste fără a citi codul din email.
 */
public interface VerificationMailSender {

    /**
     * Trimite codul către adresa dată.
     */
    void send(String email, String code, VerificationPurpose purpose, Instant expiresAt);
}
