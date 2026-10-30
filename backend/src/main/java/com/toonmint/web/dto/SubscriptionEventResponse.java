package com.toonmint.web.dto;

import com.toonmint.domain.EventProcessingStatus;

import java.time.Instant;

/**
 * Un eveniment RevenueCat afișat în consola de operator.
 */
public record SubscriptionEventResponse(
        Long id,
        String eventId,
        String eventType,
        String productId,
        EventProcessingStatus status,
        String message,
        Instant receivedAt) {
}
