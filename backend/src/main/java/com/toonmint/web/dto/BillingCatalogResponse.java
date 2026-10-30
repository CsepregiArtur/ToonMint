package com.toonmint.web.dto;

import java.util.List;

/**
 * Catalogul de planuri și pachete, plus starea configurării RevenueCat.
 */
public record BillingCatalogResponse(
        boolean configured,
        String entitlementId,
        List<CreditPackResponse> plans,
        List<CreditPackResponse> packs) {
}
