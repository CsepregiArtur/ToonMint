package com.toonmint.web.dto;

/**
 * Rezultatul procesării unui webhook RevenueCat.
 */
public record WebhookResponse(String status, String eventId, String message) {
}
