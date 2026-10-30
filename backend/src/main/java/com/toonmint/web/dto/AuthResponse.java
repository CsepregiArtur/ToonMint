package com.toonmint.web.dto;

import java.time.Instant;

/**
 * Răspunsul de autentificare: token JWT, momentul expirării și profilul utilizatorului.
 */
public record AuthResponse(String token, Instant expiresAt, UserResponse user) {
}
