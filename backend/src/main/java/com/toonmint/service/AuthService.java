package com.toonmint.service;

import com.toonmint.config.AppProperties;
import com.toonmint.domain.User;
import com.toonmint.domain.VerificationPurpose;
import com.toonmint.ratelimit.RateLimitService;
import com.toonmint.repository.UserRepository;
import com.toonmint.security.JwtService;
import com.toonmint.web.dto.AuthResponse;
import com.toonmint.web.dto.CodeSentResponse;
import com.toonmint.web.dto.LoginRequest;
import com.toonmint.web.dto.PendingVerificationResponse;
import com.toonmint.web.dto.RegisterRequest;
import com.toonmint.web.dto.ResetPasswordRequest;
import com.toonmint.web.dto.UserResponse;
import com.toonmint.web.dto.VerifyEmailRequest;
import com.toonmint.web.error.ApiException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VerificationCodeService verificationCodes;
    private final ApplicationEventPublisher events;
    private final RateLimitService rateLimitService;
    private final AppProperties properties;

    /**
     * Leagă repository-ul de utilizatori, encoder-ul de parole, serviciul JWT,
     * emiterea codurilor OTP și rate limiting-ul.
     */
    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       VerificationCodeService verificationCodes,
                       ApplicationEventPublisher events,
                       RateLimitService rateLimitService,
                       AppProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.verificationCodes = verificationCodes;
        this.events = events;
        this.rateLimitService = rateLimitService;
        this.properties = properties;
    }

    /**
     * Creează contul, trimite codul de verificare și cere confirmarea emailului
     * înainte de a emite un token.
     */
    @Transactional
    public PendingVerificationResponse register(RegisterRequest request, String clientIp) {
        User user = createUser(request, clientIp);
        return pendingVerification(user.getEmail());
    }

    /**
     * Validează unicitatea emailului, persistă utilizatorul cu parola hash-uită și
     * îi trimite codul de confirmare a adresei.
     */
    @Transactional
    public User createUser(RegisterRequest request, String clientIp) {
        String email = normalizeEmail(request.email());
        rateLimitService.check("signup-email:" + email);
        rateLimitService.check("signup-ip:" + ipKey(clientIp));
        if (request.acceptedTerms() == null || !request.acceptedTerms()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TERMS_NOT_ACCEPTED",
                    "The terms and the privacy policy must be accepted to create an account");
        }
        String currentVersion = properties.legal().termsVersion();
        if (!request.termsVersion().trim().equals(currentVersion)) {
            throw new ApiException(HttpStatus.CONFLICT, "TERMS_VERSION_OUTDATED",
                    "The legal documents have changed. Reload the page and accept the current version.",
                    Map.of("currentVersion", currentVersion));
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = new User(email, passwordEncoder.encode(request.password()),
                request.displayName() == null ? null : request.displayName().trim());
        // Dovada consimțământului: versiunea documentelor + momentul acceptării.
        user.acceptTerms(request.termsVersion().trim(), Instant.now());
        userRepository.save(user);
        issueCode(user, VerificationPurpose.EMAIL_VERIFICATION);
        return user;
    }

    /**
     * Verifică emailul și parola, apoi emite un token JWT doar pentru conturile confirmate.
     *
     * <p>Limitarea se face pe două chei: IP-ul oprește un singur host care încearcă
     * multe parole, iar emailul oprește un atac distribuit pe același cont.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request, String clientIp) {
        rateLimitService.check("login-ip:" + ipKey(clientIp));
        rateLimitService.check("login-email:" + normalizeEmail(request.email()));
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(AuthService::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (!user.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
                    "Confirm your email address before signing in",
                    Map.of("email", user.getEmail()));
        }
        return issueToken(user);
    }

    /**
     * Confirmă adresa de email pe baza codului primit și autentifică utilizatorul.
     */
    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(AuthService::invalidCode);
        if (!verificationCodes.verify(user, VerificationPurpose.EMAIL_VERIFICATION, request.code())) {
            throw invalidCode();
        }
        if (!user.isEmailVerified()) {
            user.markEmailVerified();
            userRepository.save(user);
        }
        return issueToken(user);
    }

    /**
     * Retrimite codul de verificare. Răspunsul este identic pentru adresele fără
     * cont, ca să nu se poată afla ce emailuri sunt înregistrate.
     */
    @Transactional
    public CodeSentResponse resendVerification(String email, String clientIp) {
        String normalized = normalizeEmail(email);
        rateLimitService.check("otp-email:" + normalized);
        rateLimitService.check("otp-ip:" + ipKey(clientIp));
        userRepository.findByEmailIgnoreCase(normalized)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> issueCode(user, VerificationPurpose.EMAIL_VERIFICATION));
        return codeSent();
    }

    /**
     * Trimite codul de resetare a parolei, cu răspuns identic indiferent dacă
     * adresa are cont.
     */
    @Transactional
    public CodeSentResponse forgotPassword(String email, String clientIp) {
        String normalized = normalizeEmail(email);
        rateLimitService.check("otp-email:" + normalized);
        rateLimitService.check("otp-ip:" + ipKey(clientIp));
        userRepository.findByEmailIgnoreCase(normalized)
                .ifPresent(user -> issueCode(user, VerificationPurpose.PASSWORD_RESET));
        return codeSent();
    }

    /**
     * Setează parola nouă după validarea codului. Confirmarea codului dovedește
     * accesul la email, deci contul este marcat și ca verificat.
     */
    @Transactional
    public AuthResponse resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(AuthService::invalidCode);
        if (!verificationCodes.verify(user, VerificationPurpose.PASSWORD_RESET, request.code())) {
            throw invalidCode();
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        if (!user.isEmailVerified()) {
            user.markEmailVerified();
        }
        userRepository.save(user);
        return issueToken(user);
    }

    /**
     * Construiește răspunsul de autentificare cu token și profil.
     */
    public AuthResponse issueToken(User user) {
        JwtService.Token token = jwtService.issue(user);
        return new AuthResponse(token.value(), token.expiresAt(),
                UserResponse.from(user, Instant.now()));
    }

    /**
     * Emite un cod și publică evenimentul care declanșează emailul după commit.
     */
    private void issueCode(User user, VerificationPurpose purpose) {
        VerificationCodeService.IssuedCode issued = verificationCodes.issue(user, purpose);
        events.publishEvent(new VerificationCodeIssuedEvent(
                user.getEmail(), issued.code(), purpose, issued.expiresAt()));
    }

    /**
     * Răspunsul standard după înregistrare.
     */
    private static PendingVerificationResponse pendingVerification(String email) {
        return new PendingVerificationResponse(email, true,
                VerificationCodeService.CODE_TTL.toSeconds(),
                VerificationCodeService.RESEND_COOLDOWN.toSeconds());
    }

    /**
     * Răspunsul standard pentru cererile de cod.
     */
    private static CodeSentResponse codeSent() {
        return new CodeSentResponse(true, VerificationCodeService.RESEND_COOLDOWN.toSeconds());
    }

    /**
     * Normalizează adresa de email pentru căutare și stocare.
     */
    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Cheia de rate limiting pentru un IP necunoscut.
     */
    private static String ipKey(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
    }

    /**
     * Eroare pentru credențiale invalide.
     */
    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Invalid email or password");
    }

    /**
     * Eroare pentru cod OTP invalid, expirat sau consumat.
     */
    private static ApiException invalidCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CODE",
                "The code is invalid or expired");
    }
}
