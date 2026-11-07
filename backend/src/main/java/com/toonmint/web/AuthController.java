package com.toonmint.web;

import com.toonmint.service.AuthService;
import com.toonmint.web.dto.AuthResponse;
import com.toonmint.web.dto.CodeSentResponse;
import com.toonmint.web.dto.ForgotPasswordRequest;
import com.toonmint.web.dto.LoginRequest;
import com.toonmint.web.dto.PendingVerificationResponse;
import com.toonmint.web.dto.RefreshTokenRequest;
import com.toonmint.web.dto.RegisterRequest;
import com.toonmint.web.dto.ResendVerificationRequest;
import com.toonmint.web.dto.ResetPasswordRequest;
import com.toonmint.web.dto.VerifyEmailRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    /**
     * Injectează serviciul de autentificare.
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Creează contul și trimite codul de verificare a emailului.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account and send an email verification code")
    public PendingVerificationResponse register(@Valid @RequestBody RegisterRequest request,
                                                HttpServletRequest httpRequest) {
        return authService.register(request, ClientIp.of(httpRequest));
    }

    /**
     * Confirmă adresa de email cu codul primit și returnează tokenul JWT.
     */
    @PostMapping("/verify-email")
    @Operation(summary = "Confirm the email address with the received code and return a JWT")
    public AuthResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return authService.verifyEmail(request);
    }

    /**
     * Retrimite codul de verificare a emailului.
     */
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Send a new email verification code")
    public CodeSentResponse resendVerification(@Valid @RequestBody ResendVerificationRequest request,
                                               HttpServletRequest httpRequest) {
        return authService.resendVerification(request.email(), ClientIp.of(httpRequest));
    }

    /**
     * Trimite codul de resetare a parolei.
     */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Send a password reset code")
    public CodeSentResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                           HttpServletRequest httpRequest) {
        return authService.forgotPassword(request.email(), ClientIp.of(httpRequest));
    }

    /**
     * Setează parola nouă pe baza codului primit și returnează tokenul JWT.
     */
    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password using the received code and return a JWT")
    public AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    /**
     * Autentifică utilizatorul și returnează tokenul JWT.
     */
    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for a JWT")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, ClientIp.of(httpRequest));
    }

    /**
     * Reînnoiește sesiunea: consumă tokenul de refresh și întoarce un JWT nou.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new JWT")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request,
                                HttpServletRequest httpRequest) {
        return authService.refresh(request, ClientIp.of(httpRequest));
    }

    /**
     * Închide sesiunea anulând tokenul de refresh.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke the refresh token of the current session")
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
    }
}
