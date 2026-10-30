package com.toonmint;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobApiTest extends ApiTestSupport {

    @Test
    void fullJobLifecycle() throws Exception {
        String email = uniqueEmail("job");
        String token = registerAndGetToken(email);
        giveCredits(email, 5);

        MvcResult created = mockMvc.perform(multipart("/api/jobs")
                        .file(new MockMultipartFile("image", "photo.png", "image/png", pngPhoto(1600, 1200)))
                        .param("style_id", "anime_v1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.styleId").value("anime_v1"))
                .andExpect(jsonPath("$.styleLabel").value("Anime"))
                .andReturn();
        long jobId = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asLong();

        awaitJobStatus(token, jobId, "COMPLETED", Duration.ofSeconds(10));

        byte[] image = mockMvc.perform(get("/api/jobs/" + jobId + "/download")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/")))
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(image.length > 1000, "downloaded image should not be empty");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditBalance").value(4));

        mockMvc.perform(get("/api/credits/transactions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("CONSUMPTION"))
                .andExpect(jsonPath("$.items[0].amount").value(-1));

        Long userId = userByEmail(email).getId();
        mockMvc.perform(get("/api/users/" + userId + "/jobs")
                        .param("status", "COMPLETED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/api/jobs/search")
                        .param("keyword", "anime")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(delete("/api/jobs/" + jobId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/jobs/" + jobId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REMOVED"));

        mockMvc.perform(get("/api/jobs/" + jobId + "/download")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB_NOT_COMPLETED"));
    }

    @Test
    void base64UploadWorks() throws Exception {
        String email = uniqueEmail("base64");
        String token = registerAndGetToken(email);
        giveCredits(email, 2);

        String body = objectMapper.writeValueAsString(new Base64Payload(
                "cartoon_v2",
                "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(pngPhoto(64, 64))));

        String response = mockMvc.perform(post("/api/jobs")
                        .contentType("application/json")
                        .content(body)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        long jobId = objectMapper.readTree(response).path("id").asLong();

        JsonNode job = awaitJobStatus(token, jobId, "COMPLETED", Duration.ofSeconds(10));
        assertEquals("cartoon_v2", job.path("styleId").asText());
    }

    @Test
    void unknownStyleIsRejected() throws Exception {
        String email = uniqueEmail("style");
        String token = registerAndGetToken(email);
        giveCredits(email, 2);

        mockMvc.perform(multipart("/api/jobs")
                        .file(new MockMultipartFile("image", "photo.png", "image/png", pngPhoto(32, 32)))
                        .param("style_id", "does_not_exist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.creditBalance").value(2));
    }

    @Test
    void nonImageUploadIsRejected() throws Exception {
        String email = uniqueEmail("notimage");
        String token = registerAndGetToken(email);
        giveCredits(email, 2);

        mockMvc.perform(multipart("/api/jobs")
                        .file(new MockMultipartFile("image", "notes.txt", "text/plain",
                                "this is definitely not an image".getBytes()))
                        .param("style_id", "anime_v1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    record Base64Payload(String styleId, String imageBase64) {
    }
}
