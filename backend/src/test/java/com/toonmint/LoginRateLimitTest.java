package com.toonmint;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login-ul trebuie limitat pe IP și pe email: fără asta, un atacator poate încerca
 * parole la nesfârșit (credential stuffing / brute force).
 *
 * <p>Limita e 2 cereri/minut, ca înregistrarea (care consumă cheile `signup-*`) să
 * treacă, iar al treilea login să fie respins.
 */
@SpringBootTest(properties = "app.rate-limit.requests-per-minute=2")
class LoginRateLimitTest extends ApiTestSupport {

    private static final String PASSWORD = "sup3r-secret-pass";

    @Test
    void thirdLoginAttemptInTheWindowIsThrottled() throws Exception {
        String email = uniqueEmail("loginlimit");
        registerAndGetToken(email);

        login(email, PASSWORD).andExpect(status().isOk());
        login(email, PASSWORD).andExpect(status().isOk());
        login(email, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }
}
