package com.toonmint;

import com.toonmint.domain.VerificationPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailVerificationApiTest extends ApiTestSupport {

    @Test
    void wrongCodeDoesNotVerifyAccount() throws Exception {
        String email = uniqueEmail("otp-wrong");
        registerPending(email);
        String code = verificationMail.code(email);

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(email, wrongCode(code, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CODE"));

        assertFalse(userByEmail(email).isEmailVerified());

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(email, code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        assertTrue(userByEmail(email).isEmailVerified());
    }

    @Test
    void tooManyWrongAttemptsInvalidateTheCode() throws Exception {
        String email = uniqueEmail("otp-brute");
        registerPending(email);
        String code = verificationMail.code(email);

        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(post("/api/auth/verify-email")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verifyBody(email, wrongCode(code, attempt))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CODE"));
        }

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(email, code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CODE"));

        assertFalse(userByEmail(email).isEmailVerified());
    }

    @Test
    void resendIsThrottledByCooldown() throws Exception {
        String email = uniqueEmail("otp-cooldown");
        registerPending(email);

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmailPayload(email))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("CODE_COOLDOWN"));
    }

    @Test
    void resendForUnknownAddressReturnsAcceptedWithoutSendingCode() throws Exception {
        String email = uniqueEmail("otp-unknown");

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmailPayload(email))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sent").value(true));

        assertFalse(verificationMail.hasCode(email, VerificationPurpose.EMAIL_VERIFICATION));
    }

    @Test
    void forgotPasswordForUnknownAddressDoesNotSendCode() throws Exception {
        String email = uniqueEmail("otp-forgot-unknown");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmailPayload(email))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sent").value(true));

        assertFalse(verificationMail.hasCode(email, VerificationPurpose.PASSWORD_RESET));
    }

    @Test
    void passwordResetReplacesPasswordAndVerifiesEmail() throws Exception {
        String email = uniqueEmail("otp-reset");
        registerPending(email);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmailPayload(email))))
                .andExpect(status().isAccepted());

        String code = verificationMail.code(email, VerificationPurpose.PASSWORD_RESET);
        String newPassword = "brand-new-pass-123";

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPayload(email, code, newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        assertTrue(userByEmail(email).isEmailVerified());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(email, "sup3r-secret-pass"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(email, newPassword))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void resetCodeCannotBeReused() throws Exception {
        String email = uniqueEmail("otp-reset-reuse");
        registerPending(email);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmailPayload(email))))
                .andExpect(status().isAccepted());

        String code = verificationMail.code(email, VerificationPurpose.PASSWORD_RESET);
        String payload = objectMapper.writeValueAsString(new ResetPayload(email, code, "first-new-pass-123"));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPayload(email, code, "second-new-pass-123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CODE"));
    }

    private String verifyBody(String email, String code) throws Exception {
        return objectMapper.writeValueAsString(new VerifyPayload(email, code));
    }

    /**
     * Construiește un cod de 6 cifre diferit de cel corect.
     */
    private static String wrongCode(String correct, int offset) {
        int value = (Integer.parseInt(correct) + offset) % 1_000_000;
        return String.format("%06d", value);
    }

    private record EmailPayload(String email) {
    }

    private record LoginPayload(String email, String password) {
    }

    private record ResetPayload(String email, String code, String newPassword) {
    }
}
