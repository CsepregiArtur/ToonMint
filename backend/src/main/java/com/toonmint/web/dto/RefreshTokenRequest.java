package com.toonmint.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Cerere care transportă un token de refresh: `POST /api/auth/refresh` și
 * `POST /api/auth/logout`.
 */
public record RefreshTokenRequest(@NotBlank String refreshToken) {
}
