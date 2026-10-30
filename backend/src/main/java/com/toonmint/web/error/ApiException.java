package com.toonmint.web.error;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    /**
     * Creează o eroare API cu status, cod și mesaj.
     */
    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    /**
     * Creează o eroare API cu detalii suplimentare pentru client.
     */
    public ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? Map.of() : details;
    }

    /**
     * Returnează statusul HTTP al erorii.
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * Returnează codul aplicativ al erorii.
     */
    public String getCode() {
        return code;
    }

    /**
     * Returnează detaliile suplimentare ale erorii.
     */
    public Map<String, Object> getDetails() {
        return details;
    }

    /**
     * Creează o eroare 404.
     */
    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    /**
     * Creează o eroare 400.
     */
    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    /**
     * Creează o eroare 403.
     */
    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message);
    }

    /**
     * Creează o eroare 409 cu cod aplicativ.
     */
    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
