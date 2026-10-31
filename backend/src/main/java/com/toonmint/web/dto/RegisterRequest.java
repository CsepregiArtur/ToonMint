package com.toonmint.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cerere de creare cont cu email, parolă și nume afișat.
 *
 * <p>`acceptedTerms` și `termsVersion` nu sunt opționale: acceptarea termenilor la
 * înregistrare trebuie dovedită (GDPR art. 7(1)), iar versiunea acceptată se salvează
 * în `users.terms_version`.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 100) String displayName,
        @NotNull Boolean acceptedTerms,
        @NotBlank @Size(max = 20) String termsVersion) {
}
