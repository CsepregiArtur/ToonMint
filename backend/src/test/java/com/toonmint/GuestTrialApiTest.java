package com.toonmint;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GuestTrialApiTest extends ApiTestSupport {

    @Test
    void anonymousUserGetsOneFreeImage() throws Exception {
        String publicId = createTrial(uniqueEmail("guest"), "anime_v1");

        JsonNode job = awaitGuestJob(publicId, "COMPLETED", Duration.ofSeconds(10));
        assertEquals(0, job.path("creditsConsumed").asInt());
        assertTrue(job.path("trial").asBoolean());
        assertEquals("anime_v1", job.path("styleId").asText());
        assertTrue(job.path("userId").isNull(), "trial jobs have no user");

        byte[] image = mockMvc.perform(get("/api/public/jobs/" + publicId + "/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/")))
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(image.length > 500, "downloaded trial image should not be empty");
    }

    @Test
    void secondTrialWithTheSameEmailIsBlocked() throws Exception {
        String email = uniqueEmail("once");
        createTrial(email, "anime_v1");

        mockMvc.perform(multipart("/api/public/trial")
                        .file(new MockMultipartFile("image", "photo.png", "image/png", pngPhoto(64, 64)))
                        .param("email", email)
                        .param("style_id", "cartoon_v2"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("TRIAL_ALREADY_USED"))
                .andExpect(jsonPath("$.details.subscribeEndpoint").value("/billing/catalog"));
    }

    @Test
    void differentEmailsGetSeparateTrials() throws Exception {
        createTrial(uniqueEmail("a"), "anime_v1");
        String second = createTrial(uniqueEmail("b"), "cartoon_v2");
        awaitGuestJob(second, "COMPLETED", Duration.ofSeconds(10));
    }

    @Test
    void eligibilityReflectsUsage() throws Exception {
        String email = uniqueEmail("eligibility");
        mockMvc.perform(get("/api/public/trial/eligibility").param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true));

        createTrial(email, "anime_v1");

        mockMvc.perform(get("/api/public/trial/eligibility").param("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false));
    }

    @Test
    void invalidEmailIsRejectedWithoutCreatingAJob() throws Exception {
        mockMvc.perform(multipart("/api/public/trial")
                        .file(new MockMultipartFile("image", "photo.png", "image/png", pngPhoto(32, 32)))
                        .param("email", "nu-e-email")
                        .param("style_id", "anime_v1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownTrialJobReturns404() throws Exception {
        mockMvc.perform(get("/api/public/jobs/" + java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private String createTrial(String email, String styleId) throws Exception {
        String body = mockMvc.perform(multipart("/api/public/trial")
                        .file(new MockMultipartFile("image", "photo.png", "image/png", pngPhoto(80, 60)))
                        .param("email", email)
                        .param("style_id", styleId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.publicId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("publicId").asText();
    }
}
