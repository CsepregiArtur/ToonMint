package com.toonmint;

import com.fasterxml.jackson.databind.JsonNode;
import com.toonmint.domain.VerificationPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefreshTokenApiTest extends ApiTestSupport {

    @Test
    void loginReturnsRefreshTokenThatCanBeExchangedForANewJwt() throws Exception {
        String email = uniqueEmail("refresh");
        String token = registerAndGetToken(email);

        JsonNode session = login(email);
        String refreshToken = session.path("refreshToken").asText();
        assertFalse(refreshToken.isBlank(), "login must return a refresh token");
        assertFalse(session.path("refreshExpiresAt").asText().isBlank());

        JsonNode renewed = refresh(refreshToken);
        assertNotEquals(refreshToken, renewed.path("refreshToken").asText(),
                "the refresh token must be rotated");
        assertEquals(email, renewed.path("user").path("email").asText());

        // JWT-ul nou este acceptat, deci sesiunea continuă fără parolă.
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + renewed.path("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        // JWT-ul vechi era încă valid (24h), deci reînnoirea nu l-a invalidat.
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void reusingAConsumedRefreshTokenEndsEverySession() throws Exception {
        String email = uniqueEmail("refresh-reuse");
        registerAndGetToken(email);

        String first = login(email).path("refreshToken").asText();
        String second = refresh(first).path("refreshToken").asText();

        // Refolosirea tokenului consumat e semnal de copiere: cade și tokenul curent.
        refreshExpectingFailure(first);
        refreshExpectingFailure(second);
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String email = uniqueEmail("refresh-logout");
        registerAndGetToken(email);

        String refreshToken = login(email).path("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshPayload(refreshToken))))
                .andExpect(status().isNoContent());

        refreshExpectingFailure(refreshToken);
    }

    @Test
    void logoutWithAnUnknownTokenIsStillSuccessful() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshPayload("not-a-real-token"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void refreshRejectsAnUnknownToken() throws Exception {
        refreshExpectingFailure("not-a-real-token");
    }

    @Test
    void passwordResetRevokesExistingSessions() throws Exception {
        String email = uniqueEmail("refresh-reset");
        registerAndGetToken(email);

        String refreshToken = login(email).path("refreshToken").asText();

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmailPayload(email))))
                .andExpect(status().isAccepted());

        String code = verificationMail.code(email, VerificationPurpose.PASSWORD_RESET);
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResetPayload(email, code, "brand-new-pass-123"))))
                .andExpect(status().isOk());

        refreshExpectingFailure(refreshToken);
    }

    private JsonNode login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(email, "sup3r-secret-pass"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        String body = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshPayload(refreshToken))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void refreshExpectingFailure(String refreshToken) throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshPayload(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    private record RefreshPayload(String refreshToken) {
    }

    private record LoginPayload(String email, String password) {
    }

    private record EmailPayload(String email) {
    }

    private record ResetPayload(String email, String code, String newPassword) {
    }
}
