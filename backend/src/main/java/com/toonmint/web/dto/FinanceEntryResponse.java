package com.toonmint.web.dto;

import com.toonmint.domain.FinanceCategory;
import com.toonmint.domain.FinanceEntry;
import com.toonmint.domain.FinanceEntryKind;
import com.toonmint.domain.FinanceSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * O intrare din registrul financiar, cu suma convertită în moneda de raportare.
 */
public record FinanceEntryResponse(
        Long id,
        FinanceEntryKind kind,
        FinanceCategory category,
        BigDecimal amount,
        String currency,
        BigDecimal fxRate,
        BigDecimal amountReporting,
        FinanceSource source,
        String referenceType,
        String referenceKey,
        String productId,
        String description,
        Instant occurredAt,
        Instant createdAt) {

    /**
     * Mapează entitatea la DTO, aplicând cursul de schimb.
     */
    public static FinanceEntryResponse from(FinanceEntry entry, String reportingCurrency) {
        return new FinanceEntryResponse(
                entry.getId(),
                entry.getKind(),
                entry.getCategory(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getFxRate(),
                entry.getAmount().multiply(entry.getFxRate()).setScale(4, RoundingMode.HALF_UP),
                entry.getSource(),
                entry.getReferenceType(),
                entry.getReferenceKey(),
                entry.getProductId(),
                entry.getDescription(),
                entry.getOccurredAt(),
                entry.getCreatedAt());
    }
}
