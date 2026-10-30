package com.toonmint.service;

import com.toonmint.config.AppProperties;
import com.toonmint.domain.User;
import com.toonmint.security.AuthenticatedUser;
import com.toonmint.web.dto.BillingCatalogResponse;
import com.toonmint.web.dto.CreditPackResponse;
import com.toonmint.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Linkurile RevenueCat Web Billing. RevenueCat deține checkout-ul și bonul;
 * backendul reacționează doar la webhook-ul trimis după plată.
 */
@Service
public class BillingService {

    private final AppProperties properties;
    private final UserService userService;

    /**
     * Injectează configurația RevenueCat și serviciul de utilizatori.
     */
    public BillingService(AppProperties properties, UserService userService) {
        this.properties = properties;
        this.userService = userService;
    }

    /**
     * Returnează planurile și pachetele, cu linkuri de checkout personalizate.
     */
    public BillingCatalogResponse catalog(AuthenticatedUser currentUser) {
        User user = userService.requireUser(currentUser.id());
        List<CreditPackResponse> plans = properties.revenueCat().products().stream()
                .filter(product -> product.kind() == AppProperties.RevenueCat.ProductKind.SUBSCRIPTION)
                .map(product -> toResponse(product, user))
                .toList();
        List<CreditPackResponse> packs = properties.revenueCat().products().stream()
                .filter(product -> product.kind() == AppProperties.RevenueCat.ProductKind.CREDIT_PACK)
                .map(product -> toResponse(product, user))
                .toList();
        return new BillingCatalogResponse(isConfigured(), properties.revenueCat().entitlementId(), plans, packs);
    }

    /**
     * Generează linkul RevenueCat pentru un pachet de credite.
     */
    public CreditPackResponse purchaseCredits(AuthenticatedUser currentUser, String packId) {
        AppProperties.RevenueCat.Product product = properties.revenueCat().products().stream()
                .filter(candidate -> candidate.id().equals(packId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Unknown credit pack " + packId));
        if (product.kind() != AppProperties.RevenueCat.ProductKind.CREDIT_PACK) {
            throw ApiException.badRequest("Product " + packId + " is not a credit pack");
        }
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENTS_NOT_CONFIGURED",
                    "RevenueCat purchase links are not configured on this environment");
        }
        return toResponse(product, userService.requireUser(currentUser.id()));
    }

    /**
     * Mapează un produs RevenueCat la DTO-ul de catalog.
     */
    private CreditPackResponse toResponse(AppProperties.RevenueCat.Product product, User user) {
        return new CreditPackResponse(
                product.id(),
                product.kind().name(),
                product.label(),
                product.price(),
                product.credits() == null ? 0 : product.credits(),
                isConfigured() ? buildLink(product.id(), user.getPublicId().toString()) : null);
    }

    /**
     * Verifică dacă linkul de checkout este configurat.
     */
    private boolean isConfigured() {
        String base = properties.revenueCat().purchaseLinkBaseUrl();
        return base != null && !base.isBlank();
    }

    /**
     * Construiește URL-ul de checkout cu app_user_id și product_id.
     */
    private String buildLink(String productId, String appUserId) {
        String base = properties.revenueCat().purchaseLinkBaseUrl().trim();
        String separator = base.contains("?") ? "&" : "?";
        return base + separator
                + "app_user_id=" + URLEncoder.encode(appUserId, StandardCharsets.UTF_8)
                + "&product_id=" + URLEncoder.encode(productId, StandardCharsets.UTF_8);
    }
}
