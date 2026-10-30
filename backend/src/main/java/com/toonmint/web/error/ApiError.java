package com.toonmint.web.error;

import java.time.Instant;
import java.util.Map;

/**
 * Corpul JSON standard pentru erorile API.
 */
public record ApiError(Instant timestamp, int status, String code, String message, Map<String, Object> details) {

    /**
     * Creează un răspuns de eroare fără detalii.
     */
    public static ApiError of(int status, String code, String message) {
        return new ApiError(Instant.now(), status, code, message, Map.of());
    }

    /**
     * Creează un răspuns de eroare cu detalii.
     */
    public static ApiError of(int status, String code, String message, Map<String, Object> details) {
        return new ApiError(Instant.now(), status, code, message, details == null ? Map.of() : details);
    }
}
