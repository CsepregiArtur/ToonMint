package com.toonmint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * O linie din registrul financiar. Sumele sunt în {@code currency} și se convertesc
 * în moneda de raportare cu {@code fxRate} (snapshot la momentul înregistrării, ca
 * o schimbare ulterioară de curs să nu rescrie istoricul).
 */
@Entity
@Table(name = "finance_entries")
public class FinanceEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FinanceEntryKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FinanceCategory category;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "fx_rate", nullable = false, precision = 14, scale = 6)
    private BigDecimal fxRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FinanceSource source;

    @Column(name = "reference_type", length = 24)
    private String referenceType;

    @Column(name = "reference_key", length = 255)
    private String referenceKey;

    @Column(name = "product_id", length = 100)
    private String productId;

    @Column(length = 255)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Constructor cerut de JPA.
     */
    protected FinanceEntry() {
    }

    /**
     * Creează o intrare în registrul financiar.
     */
    public FinanceEntry(FinanceCategory category,
                        BigDecimal amount,
                        String currency,
                        BigDecimal fxRate,
                        FinanceSource source,
                        String referenceType,
                        String referenceKey,
                        String productId,
                        String description,
                        Instant occurredAt) {
        this.kind = category.kind();
        this.category = category;
        this.amount = amount;
        this.currency = currency;
        this.fxRate = fxRate;
        this.source = source;
        this.referenceType = referenceType;
        this.referenceKey = referenceKey;
        this.productId = productId;
        this.description = description;
        this.occurredAt = occurredAt;
    }

    /**
     * Setează timestamp-ul înregistrării.
     */
    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (occurredAt == null) {
            occurredAt = createdAt;
        }
    }

    /**
     * Returnează identificatorul intrării.
     */
    public Long getId() {
        return id;
    }

    /**
     * Returnează direcția (încasare sau cheltuială).
     */
    public FinanceEntryKind getKind() {
        return kind;
    }

    /**
     * Returnează categoria intrării.
     */
    public FinanceCategory getCategory() {
        return category;
    }

    /**
     * Returnează suma în moneda intrării.
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Returnează moneda sumei.
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Returnează cursul folosit către moneda de raportare.
     */
    public BigDecimal getFxRate() {
        return fxRate;
    }

    /**
     * Returnează sursa intrării.
     */
    public FinanceSource getSource() {
        return source;
    }

    /**
     * Returnează tipul referinței (JOB / REVENUECAT_EVENT), dacă există.
     */
    public String getReferenceType() {
        return referenceType;
    }

    /**
     * Returnează cheia referinței folosite pentru idempotență.
     */
    public String getReferenceKey() {
        return referenceKey;
    }

    /**
     * Returnează identificatorul produsului vândut, pentru încasări.
     */
    public String getProductId() {
        return productId;
    }

    /**
     * Returnează descrierea lizibilă a intrării.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returnează momentul economic al intrării (plata, generarea, cheltuiala).
     */
    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * Returnează momentul în care a fost scrisă intrarea.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
