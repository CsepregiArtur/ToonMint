package com.toonmint.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cerere de trimitere a codului de resetare a parolei.
 */
public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 255) String email) {
}
