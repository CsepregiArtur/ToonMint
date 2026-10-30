package com.toonmint.security;

import com.toonmint.domain.UserRole;

public record AuthenticatedUser(Long id, String email, UserRole role) {

    /**
     * Indică dacă utilizatorul autentificat are rol de operator.
     */
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
