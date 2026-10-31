package com.toonmint.web.dto;

import java.time.Instant;

/**
 * Răspunsul după salvarea confirmării de la checkout.
 */
public record PurchaseAcknowledgementResponse(
        String productId,
        boolean immediateDelivery,
        boolean withdrawalWaived,
        Instant acceptedAt) {
}
