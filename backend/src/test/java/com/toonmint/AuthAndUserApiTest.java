package com.toonmint;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthAndUserApiTest extends ApiTestSupport {

    @Test
    void registerLoginAndFetchProfile() throws Exception {
        String email = uniqueEmail("auth");
        String registerBody = objectMapper.writeValueAsString(
                new RegisterPayload(email, "sup3r-secret-pass", "Ada"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.creditBalance").value(0));

        String loginBody = objectMapper.writeValueAsString(new LoginPayload(email, "sup3r-secret-pass"));
        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.displayName").value("Ada"))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginResponse).path("token").asText();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String email = uniqueEmail("dup");
        String body = objectMapper.writeValueAsString(new RegisterPayload(email, "sup3r-secret-pass", "Dup"));

        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void anonymousAccessIsRejected() throws Exception {
        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersCannotReadEachOthersProfile() throws Exception {
        String token = registerAndGetToken(uniqueEmail("owner"));
        String otherToken = registerAndGetToken(uniqueEmail("intruder"));
        Long otherId = objectMapper.readTree(mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + otherToken))
                .andReturn().getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(get("/api/users/" + otherId).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateDisplayName() throws Exception {
        String email = uniqueEmail("update2");
        String token = registerAndGetToken(email);
        Long id = userByEmail(email).getId();
        String body = objectMapper.writeValueAsString(new UpdatePayload("Ada Lovelace"));

        mockMvc.perform(put("/api/users/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ada Lovelace"));
    }

    record LoginPayload(String email, String password) {
    }

    record UpdatePayload(String displayName) {
    }
}
