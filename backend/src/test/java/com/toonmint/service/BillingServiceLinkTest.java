package com.toonmint.service;

import com.toonmint.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BillingServiceLinkTest {

    private static final String USER_ID = "0f4b3b3e-2f6b-4a0e-9c1a-6d4a1f7b2c11";

    @Test
    void appendsAppUserIdAsPathSegment() {
        BillingService service = service("https://pay.rev.cat/token123");

        assertEquals("https://pay.rev.cat/token123/" + USER_ID,
                service.buildLink(product(null), USER_ID));
    }

    @Test
    void addsPackageIdToPreselectTheProduct() {
        BillingService service = service("https://pay.rev.cat/token123");

        assertEquals("https://pay.rev.cat/token123/" + USER_ID + "?package_id=%24rc_custom",
                service.buildLink(product("$rc_custom"), USER_ID));
    }

    @Test
    void keepsConfiguredQueryString() {
        BillingService service = service("https://pay.rev.cat/token123?email=user%40example.com");

        assertEquals("https://pay.rev.cat/token123/" + USER_ID + "?email=user%40example.com&package_id=pkg",
                service.buildLink(product("pkg"), USER_ID));
    }

    @Test
    void replacesPlaceholderInsteadOfAppendingPath() {
        BillingService service = service("https://pay.rev.cat/token123/{app_user_id}");

        assertEquals("https://pay.rev.cat/token123/" + USER_ID,
                service.buildLink(product(null), USER_ID));
    }

    @Test
    void keepsAlreadyCompleteLink() {
        BillingService service = service("https://pay.rev.cat/token123?app_user_id=existing");

        assertEquals("https://pay.rev.cat/token123?app_user_id=existing",
                service.buildLink(product(null), USER_ID));
    }

    private BillingService service(String purchaseLinkBaseUrl) {
        AppProperties.RevenueCat revenueCat = new AppProperties.RevenueCat(
                "webhook-token", purchaseLinkBaseUrl, "pro", List.of());
        AppProperties properties = new AppProperties(null, null, null, null, null, null,
                null, null, revenueCat, null, null, null);
        return new BillingService(properties, null, null);
    }

    private AppProperties.RevenueCat.Product product(String packageId) {
        return new AppProperties.RevenueCat.Product("toonmint_credits_20",
                AppProperties.RevenueCat.ProductKind.CREDIT_PACK, null, 20, "20 extra generations",
                "$1.99", packageId);
    }
}
