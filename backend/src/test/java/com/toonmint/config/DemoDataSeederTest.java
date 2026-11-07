package com.toonmint.config;

import com.toonmint.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Seeder-ul verifică flagul la runtime: cu `app.demo.enabled=false` nu atinge
 * baza, chiar dacă bean-ul a intrat în binar la build (Spring AOT).
 */
class DemoDataSeederTest {

    @Test
    void doesNotTouchTheDatabaseWhenDisabled() {
        AppProperties properties = mock(AppProperties.class);
        when(properties.demo()).thenReturn(new AppProperties.Demo(
                false, "demo@toonmint.test", "demo12345", 10,
                "operator@toonmint.test", "operator12345", 10));
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

        new DemoDataSeeder(properties, userRepository, passwordEncoder).run(mock(ApplicationArguments.class));

        verifyNoInteractions(userRepository);
    }
}
