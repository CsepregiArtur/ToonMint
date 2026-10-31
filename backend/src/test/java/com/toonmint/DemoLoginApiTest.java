package com.toonmint;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemoLoginApiTest extends ApiTestSupport {

    @Test
    void availabilityListsTheDemoAccounts() throws Exception {
        mockMvc.perform(get("/api/auth/demo-login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.accounts.length()").value(2))
                .andExpect(jsonPath("$.accounts[0].role").value("USER"));
    }

    @Test
    void demoLoginReturnsAUsableToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/demo-login")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).path("token").asText();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("demo@toonmint.test"));
    }

    @Test
    void demoOperatorLoginHasAdminRole() throws Exception {
        String body = mockMvc.perform(post("/api/auth/demo-login")
                        .contentType("application/json")
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).path("token").asText();

        mockMvc.perform(get("/api/jobs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
