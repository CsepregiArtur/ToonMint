package com.toonmint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "credit_transactions")
public class CreditTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CreditTransactionType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_job_id")
    private Job referenceJob;

    @Column(length = 32)
    private String provider;

    @Column(name = "external_reference")
    private String externalReference;

    @Column
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Constructor cerut de JPA.
     */
    protected CreditTransaction() {
    }

    /**
     * Creează o înregistrare în ledgerul de credite.
     */
    public CreditTransaction(User user, int amount, CreditTransactionType type, Job referenceJob) {
        this.user = user;
        this.amount = amount;
        this.type = type;
        this.referenceJob = referenceJob;
    }

    /**
     * Setează timestamp-ul tranzacției la inserare.
     */
    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Returnează identificatorul tranzacției.
     */
    public Long getId() {
        return id;
    }

    /**
     * Returnează utilizatorul afectat.
     */
    public User getUser() {
        return user;
    }

    /**
     * Returnează suma (pozitiv = creditare, negativ = consum).
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Returnează tipul tranzacției.
     */
    public CreditTransactionType getType() {
        return type;
    }

    /**
     * Returnează jobul asociat, dacă există.
     */
    public Job getReferenceJob() {
        return referenceJob;
    }

    /**
     * Returnează furnizorul tranzacției (ex. REVENUECAT).
     */
    public String getProvider() {
        return provider;
    }

    /**
     * Setează furnizorul tranzacției.
     */
    public void setProvider(String provider) {
        this.provider = provider;
    }

    /**
     * Returnează referința externă folosită pentru idempotență.
     */
    public String getExternalReference() {
        return externalReference;
    }

    /**
     * Setează referința externă.
     */
    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    /**
     * Returnează descrierea lizibilă a tranzacției.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Setează descrierea tranzacției.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returnează data tranzacției.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
