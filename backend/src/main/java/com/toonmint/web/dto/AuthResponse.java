package com.toonmint.web.dto;

import java.time.Instant;

/**
 * Răspunsul de autentificare: token JWT, momentul expirării lui, tokenul de
 * refresh care ține sesiunea între lansări și profilul utilizatorului.
 */
public record AuthResponse(String token,
                           Instant expiresAt,
                           String refreshToken,
                           Instant refreshExpiresAt,
                           UserResponse user) {
}
