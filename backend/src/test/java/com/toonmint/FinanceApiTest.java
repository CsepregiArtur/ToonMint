package com.toonmint;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinanceApiTest extends ApiTestSupport {

    private static final String WEBHOOK_TOKEN = "test-webhook-token";
    private static final Instant FROM = Instant.now().minus(Duration.ofDays(2));
    private static final Instant TO = Instant.now().plus(Duration.ofDays(2));

    @Test
    void completedGenerationRecordsAiCost() throws Exception {
        String admin = adminToken();
        JsonNode before = summary(admin);

        String email = uniqueEmail("finance-job");
        String token = registerAndGetToken(email);
        giveCredits(email, 3);
        long jobId = uploadJob(token);
        awaitJobStatus(token, jobId, "COMPLETED", Duration.ofSeconds(10));

        JsonNode entry = awaitAiCostEntry(admin, jobId);
        assertEquals("EXPENSE", entry.path("kind").asText());
        assertEquals("AUTO_JOB", entry.path("source").asText());
        assertEquals(0, entry.path("amount").decimalValue().compareTo(new BigDecimal("0.034")));

        JsonNode after = summary(admin);
        assertEquals(0, delta(before, after, "aiCost").compareTo(new BigDecimal("0.034")),
                "the completed job must add exactly one generation cost");
        assertEquals(1, after.path("generations").asLong() - before.path("generations").asLong());

        long entryId = entry.path("id").asLong();
        mockMvc.perform(delete("/api/admin/finance/entries/" + entryId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void purchaseIsRecordedOnceAndDuplicateDoesNotCountTwice() throws Exception {
        String admin = adminToken();
        JsonNode before = summary(admin);

        String email = uniqueEmail("finance-sale");
        registerAndGetToken(email);
        String appUserId = userByEmail(email).getPublicId().toString();
        String eventId = UUID.randomUUID().toString();
        long expiration = Instant.now().plus(Duration.ofDays(7)).toEpochMilli();

        postEvent(eventId, "INITIAL_PURCHASE", "toonmint_weekly_499", appUserId, expiration, null, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        JsonNode after = summary(admin);
        assertEquals(0, delta(before, after, "grossRevenue").compareTo(new BigDecimal("4.99")),
                "the price falls back to the product label when the payload has no price");
        assertEquals(0, delta(before, after, "netRevenue").compareTo(new BigDecimal("4.99")));
        assertEquals(0, delta(before, after, "aiCost").compareTo(BigDecimal.ZERO));

        assertEquals(0, productAmount(after, "toonmint_weekly_499")
                        .subtract(productAmount(before, "toonmint_weekly_499"))
                        .compareTo(new BigDecimal("4.99")),
                "revenueByProduct must include this sale");
        assertEquals(1, productSales(after, "toonmint_weekly_499") - productSales(before, "toonmint_weekly_499"));

        postEvent(eventId, "INITIAL_PURCHASE", "toonmint_weekly_499", appUserId, expiration, null, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"));

        JsonNode replayed = summary(admin);
        assertEquals(0, delta(after, replayed, "grossRevenue").compareTo(BigDecimal.ZERO),
                "a replayed webhook must never record a second sale");
    }

    @Test
    void storeFeeAndRefundAdjustRevenue() throws Exception {
        String admin = adminToken();
        JsonNode before = summary(admin);

        String email = uniqueEmail("finance-refund");
        registerAndGetToken(email);
        String appUserId = userByEmail(email).getPublicId().toString();

        postEvent(UUID.randomUUID().toString(), "NON_RENEWING_PURCHASE", "toonmint_credits_20",
                appUserId, null, new BigDecimal("10.00"), "EUR", new BigDecimal("0.7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        JsonNode afterSale = summary(admin);
        assertEquals(0, delta(before, afterSale, "grossRevenue").compareTo(new BigDecimal("10.00")));
        assertEquals(0, delta(before, afterSale, "storeFees").compareTo(new BigDecimal("3.00")));
        assertEquals(0, delta(before, afterSale, "netRevenue").compareTo(new BigDecimal("7.00")));

        postEvent(UUID.randomUUID().toString(), "REFUND", "toonmint_credits_20",
                appUserId, null, new BigDecimal("10.00"), "EUR", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IGNORED"));

        JsonNode afterRefund = summary(admin);
        assertEquals(0, delta(before, afterRefund, "refunds").compareTo(new BigDecimal("10.00")));
        assertEquals(0, delta(before, afterRefund, "netRevenue").compareTo(new BigDecimal("-3.00")),
                "refund is an expense, so it reduces net revenue");
    }

    @Test
    void manualEntryIsRecordedAndCanBeDeleted() throws Exception {
        String admin = adminToken();
        JsonNode before = summary(admin);

        String payload = objectMapper.writeValueAsString(new ExpensePayload(
                "HOSTING", new BigDecimal("12.50"), "USD", "VPS October"));
        String created = mockMvc.perform(post("/api/admin/finance/entries")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.kind").value("EXPENSE"))
                .andReturn().getResponse().getContentAsString();
        long entryId = objectMapper.readTree(created).path("id").asLong();

        JsonNode afterAdd = summary(admin);
        assertEquals(0, delta(before, afterAdd, "otherCost").compareTo(new BigDecimal("12.50")));

        mockMvc.perform(get("/api/admin/finance/entries")
                        .param("category", "HOSTING")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        mockMvc.perform(delete("/api/admin/finance/entries/" + entryId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        JsonNode afterDelete = summary(admin);
        assertEquals(0, delta(before, afterDelete, "otherCost").compareTo(BigDecimal.ZERO));
    }

    @Test
    void nonPositiveManualAmountIsRejected() throws Exception {
        String admin = adminToken();
        String payload = objectMapper.writeValueAsString(new ExpensePayload(
                "MARKETING", BigDecimal.ZERO, "USD", "Invalid"));

        mockMvc.perform(post("/api/admin/finance/entries")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void financeIsOperatorOnly() throws Exception {
        String token = registerAndGetToken(uniqueEmail("finance-user"));

        mockMvc.perform(get("/api/admin/finance/summary")
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/finance/entries")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String adminToken() throws Exception {
        String email = uniqueEmail("finance-admin");
        registerAndGetToken(email);
        promoteToAdmin(email);
        String body = objectMapper.writeValueAsString(new LoginPayload(email, "sup3r-secret-pass"));
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("token").asText();
    }

    private JsonNode summary(String token) throws Exception {
        String body = mockMvc.perform(get("/api/admin/finance/summary")
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode entries(String token, String category) throws Exception {
        String body = mockMvc.perform(get("/api/admin/finance/entries")
                        .param("category", category)
                        .param("size", "100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode awaitAiCostEntry(String token, long jobId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            for (JsonNode item : entries(token, "AI_GENERATION").path("items")) {
                if (String.valueOf(jobId).equals(item.path("referenceKey").asText())) {
                    return item;
                }
            }
            Thread.sleep(25);
        }
        throw new AssertionError("No AI_GENERATION finance entry was written for job " + jobId);
    }

    private long uploadJob(String token) throws Exception {
        String response = mockMvc.perform(multipart("/api/jobs")
                        .file(new MockMultipartFile("image", "photo.png", "image/png", pngPhoto(400, 400)))
                        .param("style_id", "anime_v1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("id").asLong();
    }

    private static BigDecimal delta(JsonNode before, JsonNode after, String field) {
        return after.path(field).decimalValue().subtract(before.path(field).decimalValue());
    }

    private static JsonNode productRevenue(JsonNode summary, String productId) {
        for (JsonNode row : summary.path("revenueByProduct")) {
            if (productId.equals(row.path("productId").asText())) {
                return row;
            }
        }
        return null;
    }

    private static BigDecimal productAmount(JsonNode summary, String productId) {
        JsonNode row = productRevenue(summary, productId);
        return row == null ? BigDecimal.ZERO : row.path("amount").decimalValue();
    }

    private static long productSales(JsonNode summary, String productId) {
        JsonNode row = productRevenue(summary, productId);
        return row == null ? 0 : row.path("sales").asLong();
    }

    private ResultActions postEvent(String eventId,
                                    String type,
                                    String productId,
                                    String appUserId,
                                    Long expirationMs,
                                    BigDecimal price,
                                    String currency,
                                    BigDecimal takehome) throws Exception {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", eventId);
        event.put("type", type);
        event.put("product_id", productId);
        event.put("app_user_id", appUserId);
        if (expirationMs != null) {
            event.put("expiration_at_ms", expirationMs);
        }
        if (price != null) {
            event.put("price", price);
        }
        if (currency != null) {
            event.put("currency", currency);
        }
        if (takehome != null) {
            event.put("takehome_percentage", takehome);
        }
        String body = objectMapper.writeValueAsString(Map.of("api_version", "1.0", "event", event));
        return mockMvc.perform(post("/api/webhooks/revenuecat")
                .header("Authorization", WEBHOOK_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private record LoginPayload(String email, String password) {
    }

    private record ExpensePayload(String category, BigDecimal amount, String currency, String description) {
    }
}
