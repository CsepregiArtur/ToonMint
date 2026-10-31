package com.toonmint;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drepturile persoanei vizate și dovezile cerute de lege:
 * export (art. 20), ștergere (art. 17), consimțământ la termeni (art. 7) și
 * confirmarea de la checkout (OUG 34/2014 art. 16 lit. m).
 */
class AccountApiTest extends ApiTestSupport {

    private static final String TERMS_VERSION = "2026-10-31";

    @Test
    void registrationWithoutAcceptedTermsIsRejected() throws Exception {
        String body = objectMapper.writeValueAsString(
                new RegisterPayload(uniqueEmail("no-terms"), "sup3r-secret-pass", "No terms", false, TERMS_VERSION));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TERMS_NOT_ACCEPTED"));
    }

    @Test
    void outdatedTermsVersionIsRejected() throws Exception {
        String body = objectMapper.writeValueAsString(
                new RegisterPayload(uniqueEmail("old-terms"), "sup3r-secret-pass", "Old", true, "2020-01-01"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TERMS_VERSION_OUTDATED"))
                .andExpect(jsonPath("$.details.currentVersion").exists());
    }

    @Test
    void termsVersionIsStoredAndExported() throws Exception {
        String email = uniqueEmail("export");
        registerPending(email);
        String token = verifyEmailAndGetToken(email, verificationMail.code(email));

        mockMvc.perform(get("/api/users/me/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email").value(email))
                .andExpect(jsonPath("$.profile.termsVersion").value(TERMS_VERSION))
                .andExpect(jsonPath("$.profile.termsAcceptedAt").exists())
                .andExpect(jsonPath("$.jobs").isArray())
                .andExpect(jsonPath("$.transactions").isArray())
                .andExpect(jsonPath("$.purchaseAcknowledgements").isArray());
    }

    @Test
    void deletedAccountIsAnonymisedAndTokenStopsWorking() throws Exception {
        String email = uniqueEmail("delete");
        registerPending(email);
        String token = verifyEmailAndGetToken(email, verificationMail.code(email));

        mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Tokenul emis înainte de ștergere nu mai este acceptat.
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        // Emailul este eliberat: se poate crea un cont nou cu aceeași adresă.
        registerPending(email);
    }

    @Test
    void checkoutAcknowledgementIsRecorded() throws Exception {
        String email = uniqueEmail("ack");
        registerPending(email);
        String token = verifyEmailAndGetToken(email, verificationMail.code(email));

        String body = objectMapper.writeValueAsString(new AcknowledgementPayload("toonmint_credits_20", TERMS_VERSION));

        mockMvc.perform(post("/api/billing/acknowledgements")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("toonmint_credits_20"))
                .andExpect(jsonPath("$.immediateDelivery").value(true))
                .andExpect(jsonPath("$.withdrawalWaived").value(true))
                .andExpect(jsonPath("$.acceptedAt").exists());

        // Confirmarea apare în export, ca dovadă pentru consumator.
        mockMvc.perform(get("/api/users/me/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchaseAcknowledgements[0].productId").value("toonmint_credits_20"));
    }

    /**
     * Confirmarea trimisă de frontend înainte de checkout.
     */
    private record AcknowledgementPayload(String productId, String termsVersion) {
    }
}
