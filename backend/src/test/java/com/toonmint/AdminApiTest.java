package com.toonmint;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminApiTest extends ApiTestSupport {

    @Test
    void operatorCanSendSimulatedEmails() throws Exception {
        String email = uniqueEmail("admin");
        registerAndGetToken(email);
        promoteToAdmin(email);
        String token = login(email);

        String body = objectMapper.writeValueAsString(new EmailPayload(
                java.util.List.of(), java.util.List.of("client@example.com"), "Launch", "Hello from ToonMint"));

        mockMvc.perform(post("/api/admin/send-email")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(1))
                .andExpect(jsonPath("$.sent").value(1))
                .andExpect(jsonPath("$.simulated").value(true));
    }

    @Test
    void regularUsersCannotAccessAdminEndpoints() throws Exception {
        String token = registerAndGetToken(uniqueEmail("notadmin"));
        String body = objectMapper.writeValueAsString(new EmailPayload(
                java.util.List.of(), java.util.List.of("client@example.com"), "Hi", "Hello"));

        mockMvc.perform(post("/api/admin/send-email")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/jobs").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorCanReadStats() throws Exception {
        String email = uniqueEmail("stats");
        registerAndGetToken(email);
        promoteToAdmin(email);
        String token = login(email);

        String from = Instant.now().minus(Duration.ofDays(1)).toString();
        String to = Instant.now().plus(Duration.ofDays(1)).toString();

        mockMvc.perform(get("/api/jobs/stats")
                        .param("from", from)
                        .param("to", to)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobs").isNumber())
                .andExpect(jsonPath("$.byStyle").isArray());

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        mockMvc.perform(get("/api/admin/revenuecat-events").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    private String login(String email) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginPayload(email, "sup3r-secret-pass"));
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("token").asText();
    }

    record LoginPayload(String email, String password) {
    }

    record EmailPayload(java.util.List<Long> userIds, java.util.List<String> emails, String subject, String text) {
    }
}
