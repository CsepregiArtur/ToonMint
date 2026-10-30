package com.toonmint;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CreditsApiTest extends ApiTestSupport {

    @Test
    void generationWithoutCreditsReturns402() throws Exception {
        String email = uniqueEmail("nocredits");
        String token = registerAndGetToken(email);

        submitJob(token)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_CREDITS"))
                .andExpect(jsonPath("$.details.required").value(1))
                .andExpect(jsonPath("$.details.balance").value(0));
    }

    @Test
    void billingCatalogExposesRevenueCatLinks() throws Exception {
        String token = registerAndGetToken(uniqueEmail("catalog"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/billing/catalog").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.plans.length()").value(2))
                .andExpect(jsonPath("$.packs.length()").value(1))
                .andExpect(jsonPath("$.packs[0].purchaseUrl").value(org.hamcrest.Matchers.containsString(
                        "app_user_id=")));
    }

    @Test
    void purchaseReturnsCheckoutLinkForCreditPacks() throws Exception {
        String token = registerAndGetToken(uniqueEmail("buy"));
        String body = "{\"packId\":\"toonmint_credits_20\"}";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/credits/purchase")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credits").value(20))
                .andExpect(jsonPath("$.purchaseUrl").value(org.hamcrest.Matchers.containsString(
                        "product_id=toonmint_credits_20")));
    }

    @Test
    void purchaseRejectsSubscriptionProducts() throws Exception {
        String token = registerAndGetToken(uniqueEmail("buysub"));
        String body = "{\"packId\":\"toonmint_weekly_499\"}";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/credits/purchase")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    private ResultActions submitJob(String token) throws Exception {
        return mockMvc.perform(multipart("/api/jobs")
                .file(new MockMultipartFile("image", "photo.png", "image/png", pngPhoto(64, 64)))
                .param("style_id", "anime_v1")
                .header("Authorization", "Bearer " + token));
    }
}
