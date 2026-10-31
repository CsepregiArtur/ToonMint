package com.toonmint.service;

import com.toonmint.domain.VerificationPurpose;

import java.time.Instant;

/**
 * Eveniment publicat după ce un cod OTP a fost persistat. Trimiterea efectivă a
 * emailului are loc abia după commit, ca să nu plece coduri pentru tranzacții anulate.
 */
public record VerificationCodeIssuedEvent(String email,
                                          String code,
                                          VerificationPurpose purpose,
                                          Instant expiresAt) {
}
