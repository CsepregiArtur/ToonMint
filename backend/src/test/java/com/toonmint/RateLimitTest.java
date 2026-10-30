package com.toonmint;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.rate-limit.requests-per-minute=1")
class RateLimitTest extends ApiTestSupport {

    @Test
    void secondRequestWithinTheWindowIsRejected() throws Exception {
        String email = uniqueEmail("ratelimit");
        String token = registerAndGetToken(email);
        giveCredits(email, 5);

        submit(token).andExpect(status().isAccepted());
        submit(token)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    private org.springframework.test.web.servlet.ResultActions submit(String token) throws Exception {
        return mockMvc.perform(multipart("/api/jobs")
                .file(new MockMultipartFile("image", "photo.png", "image/png", pngPhoto(64, 64)))
                .param("style_id", "anime_v1")
                .header("Authorization", "Bearer " + token));
    }
}
