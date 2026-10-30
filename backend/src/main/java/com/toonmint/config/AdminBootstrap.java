package com.toonmint.config;

import com.toonmint.domain.User;
import com.toonmint.domain.UserRole;
import com.toonmint.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Creează primul cont de operator dacă ADMIN_EMAIL și ADMIN_PASSWORD sunt
 * setate pe un mediu nou.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(AppProperties properties, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creează contul de operator dacă ADMIN_EMAIL și ADMIN_PASSWORD sunt setate și contul nu există.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.admin().email();
        String password = properties.admin().password();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalized)) {
            return;
        }
        User admin = new User(normalized, passwordEncoder.encode(password), "ToonMint operator");
        admin.setRole(UserRole.ADMIN);
        admin.setCreditBalance(0);
        userRepository.save(admin);
        log.info("Created operator account {}", normalized);
    }
}
