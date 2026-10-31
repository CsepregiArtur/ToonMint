package com.toonmint;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StylePreviewApiTest extends ApiTestSupport {

    @Test
    void onlyOperatorsCanTouchPreviews() throws Exception {
        String token = registerAndGetToken(uniqueEmail("preview-user"));

        mockMvc.perform(get("/api/admin/style-previews").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/style-previews/regenerate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusListsEveryStyleAsMissingWithoutGeneratedFiles() throws Exception {
        String admin = adminToken();

        mockMvc.perform(get("/api/admin/style-previews").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("stub"))
                .andExpect(jsonPath("$.generativeProvider").value(false))
                .andExpect(jsonPath("$.present.length()").value(0))
                .andExpect(jsonPath("$.missing.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(20)));
    }

    @Test
    void generationRefusesToRunWithTheStubProvider() throws Exception {
        String admin = adminToken();

        mockMvc.perform(post("/api/admin/style-previews/regenerate")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREVIEW_REQUIRES_AI_PROVIDER"));
    }

    private String adminToken() throws Exception {
        String email = uniqueEmail("preview-admin");
        registerAndGetToken(email);
        promoteToAdmin(email);
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new LoginPayload(email, "sup3r-secret-pass"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("token").asText();
    }

    record LoginPayload(String email, String password) {
    }
}
