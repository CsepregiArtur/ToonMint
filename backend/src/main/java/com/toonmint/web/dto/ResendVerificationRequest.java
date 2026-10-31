package com.toonmint.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cerere de retrimitere a codului de verificare a emailului.
 */
public record ResendVerificationRequest(@NotBlank @Email @Size(max = 255) String email) {
}
