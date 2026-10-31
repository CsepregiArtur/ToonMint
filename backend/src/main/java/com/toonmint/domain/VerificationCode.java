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

import java.time.Instant;

/**
 * Cod OTP cu valabilitate limitată, folosit pentru verificarea emailului sau
 * resetarea parolei. Se păstrează doar hash-ul codului, niciodată codul în clar.
 */
@Entity
@Table(name = "verification_codes")
public class VerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private VerificationPurpose purpose;

    @Column(name = "code_hash", nullable = false, updatable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Constructor cerut de JPA.
     */
    protected VerificationCode() {
    }

    /**
     * Creează un cod nou, deja hash-uit, pentru utilizatorul și scopul dat.
     */
    public VerificationCode(Long userId, VerificationPurpose purpose, String codeHash, Instant expiresAt) {
        this.userId = userId;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    /**
     * Setează momentul creării înainte de inserare.
     */
    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Verifică dacă codul a expirat la momentul dat.
     */
    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    /**
     * Verifică dacă numărul maxim de încercări a fost atins.
     */
    public boolean isExhausted(int maxAttempts) {
        return attempts >= maxAttempts;
    }

    /**
     * Marchează codul ca folosit, ca să nu mai poată fi acceptat.
     */
    public void markConsumed(Instant now) {
        this.consumedAt = now;
    }

    /**
     * Incrementează contorul de încercări greșite.
     */
    public void registerFailedAttempt() {
        this.attempts++;
    }

    /**
     * Returnează cheia primară.
     */
    public Long getId() {
        return id;
    }

    /**
     * Returnează utilizatorul căruia îi aparține codul.
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Returnează scopul codului.
     */
    public VerificationPurpose getPurpose() {
        return purpose;
    }

    /**
     * Returnează hash-ul codului.
     */
    public String getCodeHash() {
        return codeHash;
    }

    /**
     * Returnează momentul expirării.
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Returnează numărul de încercări greșite.
     */
    public int getAttempts() {
        return attempts;
    }

    /**
     * Returnează momentul consumării codului sau null dacă e încă activ.
     */
    public Instant getConsumedAt() {
        return consumedAt;
    }

    /**
     * Returnează momentul creării.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
