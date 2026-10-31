package com.toonmint.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Extrage adresa clientului, ținând cont de proxy-urile care setează X-Forwarded-For.
 */
final class ClientIp {

    private ClientIp() {
    }

    static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
