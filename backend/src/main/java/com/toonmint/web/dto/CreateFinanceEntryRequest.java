package com.toonmint.web.dto;

import com.toonmint.domain.FinanceCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * O cheltuială sau un venit introdus manual de operator. Direcția este dedusă
 * din categorie, ca suma și sensul ei să nu poată fi contradictorii.
 */
public record CreateFinanceEntryRequest(
        @NotNull(message = "category is required")
        FinanceCategory category,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.0001", message = "amount must be greater than zero")
        @Digits(integer = 12, fraction = 4, message = "amount supports at most 4 decimals")
        BigDecimal amount,

        @Pattern(regexp = "[A-Za-z]{3}", message = "currency must be a 3-letter ISO code")
        String currency,

        @Size(max = 255, message = "description supports at most 255 characters")
        String description,

        Instant occurredAt) {
}
