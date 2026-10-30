package com.toonmint.web;

import com.toonmint.config.AppProperties;
import com.toonmint.service.RevenueCatService;
import com.toonmint.web.dto.WebhookResponse;
import com.toonmint.web.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/webhooks")
@Tag(name = "Webhooks")
public class RevenueCatWebhookController {

    private final RevenueCatService revenueCatService;
    private final AppProperties properties;

    /**
     * Injectează serviciul RevenueCat și configurația.
     */
    public RevenueCatWebhookController(RevenueCatService revenueCatService, AppProperties properties) {
        this.revenueCatService = revenueCatService;
        this.properties = properties;
    }

    /**
     * Validează header-ul Authorization și procesează evenimentul.
     */
    @PostMapping("/revenuecat")
    @Operation(summary = "RevenueCat webhook (subscriptions and credit packs)")
    public ResponseEntity<WebhookResponse> handle(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                  @RequestBody String body) {
        String expected = properties.revenueCat().webhookAuthToken();
        if (expected == null || expected.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_NOT_CONFIGURED",
                    "RevenueCat webhook token is not configured on this environment");
        }
        if (authorization == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                authorization.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE",
                    "Invalid webhook authorization header");
        }
        return ResponseEntity.ok(revenueCatService.handle(body));
    }
}
