package com.toonmint.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Cerere de autentificare cu email și parolă.
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password) {
}
