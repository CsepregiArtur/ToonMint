package com.toonmint.web;

import com.toonmint.config.AppProperties;
import com.toonmint.domain.User;
import com.toonmint.domain.UserRole;
import com.toonmint.repository.UserRepository;
import com.toonmint.service.AuthService;
import com.toonmint.web.dto.AuthResponse;
import com.toonmint.web.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Login demo pentru dezvoltare: un click, fără parolă. Endpointul răspunde doar
 * când `app.demo.enabled=true` (implicit în `dev`, mereu oprit în `prod`).
 *
 * <p>Verificarea se face la runtime, nu prin {@code @ConditionalOnProperty}:
 * în imaginea nativă GraalVM condițiile sunt evaluate de Spring AOT la build, deci
 * un bean coapte acolo ar rămâne activ în producție indiferent de valoarea citită
 * la pornire.
 */
@RestController
@RequestMapping("/api/auth/demo-login")
@Tag(name = "Authentication")
public class DemoAuthController {

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final AuthService authService;

    public DemoAuthController(AppProperties properties, UserRepository userRepository, AuthService authService) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    /**
     * Anunță UI-ul că login-ul demo este disponibil și ce conturi poate folosi.
     */
    @GetMapping
    @Operation(summary = "Demo login availability and accounts (dev only)")
    public Map<String, Object> availability() {
        requireEnabled();
        return Map.of(
                "available", true,
                "accounts", List.of(
                        Map.of("role", "USER",
                                "email", properties.demo().userEmail(),
                                "label", "Cont demo (25 credite)"),
                        Map.of("role", "ADMIN",
                                "email", properties.demo().adminEmail(),
                                "label", "Operator demo (acces admin)")));
    }

    /**
     * Emite un token JWT pentru contul demo cerut (USER implicit).
     */
    @PostMapping
    @Operation(summary = "Sign in as a demo account without a password (dev only)")
    public AuthResponse login(@RequestBody(required = false) DemoLoginRequest request) {
        requireEnabled();
        UserRole role = request == null || request.role() == null
                ? UserRole.USER
                : parseRole(request.role());
        String email = role == UserRole.ADMIN
                ? properties.demo().adminEmail()
                : properties.demo().userEmail();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEMO_NOT_READY",
                        "Contul demo nu a fost creat. Repornește aplicația."));
        return authService.issueToken(user);
    }

    /**
     * Ascunde complet endpointul când login-ul demo este dezactivat. Răspunsul e
     * identic cu cel pentru o rută inexistentă, ca să nu dea indicii despre feature.
     */
    private void requireEnabled() {
        if (!properties.demo().enabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DEMO_DISABLED", "Login demo este dezactivat.");
        }
    }

    private UserRole parseRole(String value) {
        try {
            return UserRole.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Rol demo necunoscut: " + value);
        }
    }

    /**
     * Rolul cerut: USER (implicit) sau ADMIN.
     */
    public record DemoLoginRequest(String role) {
    }
}
