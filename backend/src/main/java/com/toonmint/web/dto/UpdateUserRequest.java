package com.toonmint.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Cerere de actualizare a profilului (câmpurile absente rămân neschimbate).
 */
public record UpdateUserRequest(
        @Size(max = 100) String displayName,
        @Email @Size(max = 255) String email) {
}
