package com.toonmint.web.dto;

import com.toonmint.domain.CreditTransactionType;

import java.time.Instant;

/**
 * O linie din istoricul de credite.
 */
public record TransactionResponse(
        Long id,
        int amount,
        CreditTransactionType type,
        Long referenceJobId,
        String provider,
        String description,
        Instant createdAt) {
}
