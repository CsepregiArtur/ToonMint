package com.toonmint.web.dto;

/**
 * Un plan sau pachet de credite afișat în UI, cu linkul de checkout.
 */
public record CreditPackResponse(
        String id,
        String kind,
        String label,
        String price,
        int credits,
        String purchaseUrl) {
}
