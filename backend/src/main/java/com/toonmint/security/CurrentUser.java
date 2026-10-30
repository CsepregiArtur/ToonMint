package com.toonmint.security;

import com.toonmint.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CurrentUser {

    /**
     * Returnează utilizatorul autentificat, dacă există în contextul de securitate.
     */
    public Optional<AuthenticatedUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /**
     * Returnează utilizatorul autentificat sau aruncă 401.
     */
    public AuthenticatedUser require() {
        return find().orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"));
    }

    /**
     * Indică dacă cererea curentă aparține unui operator.
     */
    public boolean isAdmin() {
        return find().map(AuthenticatedUser::isAdmin).orElse(false);
    }
}
