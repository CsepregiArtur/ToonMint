package com.toonmint.config;

import com.toonmint.domain.SubscriptionType;
import com.toonmint.domain.User;
import com.toonmint.domain.UserRole;
import com.toonmint.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Creează conturile demo (utilizator + operator) la pornire, când
 * `app.demo.enabled=true`. Rulează doar în dezvoltare: profilul `prod` dezactivează
 * proprietatea, deci bean-ul nu există.
 */
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(AppProperties properties, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creează cele două conturi demo dacă nu există deja.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Demo demo = properties.demo();
        seed(demo.userEmail(), demo.userPassword(), demo.userCredits(), UserRole.USER, "Demo user");
        seed(demo.adminEmail(), demo.adminPassword(), demo.adminCredits(), UserRole.ADMIN, "Demo operator");
        log.warn("Demo accounts are enabled (app.demo.enabled=true). "
                + "Sign in with {} / {} or {} / {}",
                demo.userEmail(), demo.userPassword(), demo.adminEmail(), demo.adminPassword());
    }

    /**
     * Inserează un cont demo și îi acordă creditele inițiale, dacă emailul e liber.
     * Conturile demo sunt considerate de încredere, deci marcate ca verificate.
     */
    private void seed(String email, String password, int credits, UserRole role, String displayName) {
        if (email == null || email.isBlank()) {
            return;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        User existing = userRepository.findByEmailIgnoreCase(normalized).orElse(null);
        if (existing != null) {
            if (!existing.isEmailVerified()) {
                existing.markEmailVerified();
                userRepository.save(existing);
                log.info("Marked demo {} account {} as verified", role, normalized);
            }
            return;
        }
        User user = new User(normalized, passwordEncoder.encode(password), displayName);
        user.setRole(role);
        user.setCreditBalance(Math.max(0, credits));
        user.setSubscriptionType(role == UserRole.ADMIN ? SubscriptionType.YEARLY : SubscriptionType.WEEKLY);
        user.markEmailVerified();
        userRepository.save(user);
        log.info("Seeded demo {} account {} with {} credits", role, normalized, credits);
    }
}
