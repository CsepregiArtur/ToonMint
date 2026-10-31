package com.toonmint.domain;

/**
 * Scopul unui cod OTP trimis prin email.
 */
public enum VerificationPurpose {

    /** Confirmarea adresei de email după înregistrare. */
    EMAIL_VERIFICATION,

    /** Resetarea parolei pentru un cont existent. */
    PASSWORD_RESET
}
