package com.toonmint.service;

import com.toonmint.domain.User;
import com.toonmint.repository.UserRepository;
import com.toonmint.security.JwtService;
import com.toonmint.web.dto.AuthResponse;
import com.toonmint.web.dto.LoginRequest;
import com.toonmint.web.dto.RegisterRequest;
import com.toonmint.web.dto.UserResponse;
import com.toonmint.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Leagă repository-ul de utilizatori, encoder-ul de parole și serviciul JWT.
     */
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Creează contul și returnează imediat un token JWT.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        User user = createUser(request);
        return issueToken(user);
    }

    /**
     * Validează unicitatea emailului și persistă utilizatorul cu parola hash-uită.
     */
    @Transactional
    public User createUser(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = new User(email, passwordEncoder.encode(request.password()),
                request.displayName() == null ? null : request.displayName().trim());
        return userRepository.save(user);
    }

    /**
     * Verifică emailul și parola, apoi emite un token JWT.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "Invalid email or password");
        }
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
}
