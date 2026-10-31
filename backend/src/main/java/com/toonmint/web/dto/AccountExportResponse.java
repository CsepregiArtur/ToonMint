package com.toonmint.web.dto;

import com.toonmint.domain.CreditTransactionType;
import com.toonmint.domain.JobStatus;
import com.toonmint.domain.SubscriptionType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Exportul datelor unui utilizator, într-un singur document JSON (GDPR art. 20 —
 * dreptul la portabilitate). Nu conține imagini: acestea nu sunt stocate niciodată.
 */
public record AccountExportResponse(
        Instant exportedAt,
        String legalNotice,
        Profile profile,
        List<JobEntry> jobs,
        List<TransactionEntry> transactions,
        List<PurchaseEntry> purchaseAcknowledgements) {

    /**
     * Datele de profil și dovada consimțământului la termeni.
     */
    public record Profile(
            UUID publicId,
            String email,
            String displayName,
            int creditBalance,
            SubscriptionType subscriptionType,
            Instant subscriptionExpiresAt,
            Instant createdAt,
            String termsVersion,
            Instant termsAcceptedAt) {
    }

    /**
     * Metadatele unui job. Fișierele în sine sunt temporare și nu fac obiectul exportului.
     */
    public record JobEntry(
            UUID id,
            String styleId,
            JobStatus status,
            int creditsConsumed,
            Instant createdAt,
            Instant completedAt,
            String errorMessage) {
    }

    /**
     * O linie din registrul de credite.
     */
    public record TransactionEntry(
            Long id,
            int amount,
            CreditTransactionType type,
            String provider,
            String description,
            Instant createdAt) {
    }

    /**
     * Confirmarea dată la checkout (livrare imediată, renunțare la retragere).
     */
    public record PurchaseEntry(
            String productId,
            String kind,
            boolean immediateDelivery,
            boolean withdrawalWaived,
            Instant acceptedAt) {
    }
}
