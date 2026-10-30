package com.toonmint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toonmint.domain.SubscriptionType;
import com.toonmint.domain.User;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RevenueCatWebhookTest extends ApiTestSupport {

    private static final String WEBHOOK_TOKEN = "test-webhook-token";

    @Test
    void initialPurchaseActivatesSubscriptionAndGrantsCredits() throws Exception {
        String email = uniqueEmail("rc-initial");
        registerAndGetToken(email);
        User user = userByEmail(email);
        String eventId = UUID.randomUUID().toString();

        postEvent(eventId, "INITIAL_PURCHASE", "toonmint_weekly_499", user.getPublicId().toString(),
                Instant.now().plus(Duration.ofDays(7)).toEpochMilli(), WEBHOOK_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        User updated = userByEmail(email);
        assertEquals(50, updated.getCreditBalance());
        assertEquals(SubscriptionType.WEEKLY, updated.getSubscriptionType());

        postEvent(eventId, "INITIAL_PURCHASE", "toonmint_weekly_499", user.getPublicId().toString(),
                Instant.now().plus(Duration.ofDays(7)).toEpochMilli(), WEBHOOK_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"));

        assertEquals(50, userByEmail(email).getCreditBalance(),
                "replayed events must never grant credits twice");
    }

    @Test
    void creditPackPurchaseGrantsCredits() throws Exception {
        String email = uniqueEmail("rc-pack");
        registerAndGetToken(email);
        User user = userByEmail(email);

        postEvent(UUID.randomUUID().toString(), "NON_RENEWING_PURCHASE", "toonmint_credits_20",
                user.getPublicId().toString(), null, WEBHOOK_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        assertEquals(20, userByEmail(email).getCreditBalance());
    }

    @Test
    void expirationDowngradesToFree() throws Exception {
        String email = uniqueEmail("rc-expire");
        registerAndGetToken(email);
        User user = userByEmail(email);

        postEvent(UUID.randomUUID().toString(), "INITIAL_PURCHASE", "toonmint_yearly_2499",
                user.getPublicId().toString(), Instant.now().plus(Duration.ofDays(365)).toEpochMilli(),
                WEBHOOK_TOKEN).andExpect(status().isOk());

        postEvent(UUID.randomUUID().toString(), "EXPIRATION", "toonmint_yearly_2499",
                user.getPublicId().toString(), Instant.now().toEpochMilli(), WEBHOOK_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        User expired = userByEmail(email);
        assertEquals(SubscriptionType.FREE, expired.getSubscriptionType());
        assertEquals(null, expired.getSubscriptionExpiresAt());
    }

    @Test
    void refundDowngradesSubscription() throws Exception {
        String email = uniqueEmail("rc-refund");
        registerAndGetToken(email);
        User user = userByEmail(email);

        postEvent(UUID.randomUUID().toString(), "INITIAL_PURCHASE", "toonmint_weekly_499",
                user.getPublicId().toString(), Instant.now().plus(Duration.ofDays(7)).toEpochMilli(),
                WEBHOOK_TOKEN).andExpect(status().isOk());

        postEvent(UUID.randomUUID().toString(), "REFUND", "toonmint_weekly_499",
                user.getPublicId().toString(), null, WEBHOOK_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        User refunded = userByEmail(email);
        assertEquals(SubscriptionType.FREE, refunded.getSubscriptionType());
        assertEquals(null, refunded.getSubscriptionExpiresAt());
    }

    @Test
    void invalidAuthorizationIsRejected() throws Exception {
        String email = uniqueEmail("rc-unauthorized");
        registerAndGetToken(email);
        User user = userByEmail(email);

        postEvent(UUID.randomUUID().toString(), "INITIAL_PURCHASE", "toonmint_weekly_499",
                user.getPublicId().toString(), null, "wrong-token")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownProductIsIgnored() throws Exception {
        String email = uniqueEmail("rc-unknown");
        registerAndGetToken(email);
        User user = userByEmail(email);

        postEvent(UUID.randomUUID().toString(), "NON_RENEWING_PURCHASE", "not_a_real_product",
                user.getPublicId().toString(), null, WEBHOOK_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IGNORED"));
    }

    private org.springframework.test.web.servlet.ResultActions postEvent(String eventId,
                                                                        String type,
                                                                        String productId,
                                                                        String appUserId,
                                                                        Long expirationMs,
                                                                        String token) throws Exception {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", eventId);
        event.put("type", type);
        event.put("product_id", productId);
        event.put("app_user_id", appUserId);
        if (expirationMs != null) {
            event.put("expiration_at_ms", expirationMs);
        }
        String body = new ObjectMapper().writeValueAsString(Map.of("api_version", "1.0", "event", event));
        return mockMvc.perform(post("/api/webhooks/revenuecat")
                .header("Authorization", token)
                .contentType("application/json")
                .content(body));
    }
}
