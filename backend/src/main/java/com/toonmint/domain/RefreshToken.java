package com.toonmint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Token de refresh cu viață lungă, din care se emit JWT-uri noi fără relogin.
 * Se păstrează doar hash-ul tokenului, niciodată valoarea în clar.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /**
     * Constructor cerut de JPA.
     */
    protected RefreshToken() {
    }

    /**
     * Creează un token nou, deja hash-uit, pentru utilizatorul dat.
     */
    public RefreshToken(Long userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /**
     * Setează momentul emiterii înainte de inserare.
     */
    @PrePersist
    void onCreate() {
        if (issuedAt == null) {
            issuedAt = Instant.now();
        }
    }

    /**
     * Verifică dacă tokenul a expirat la momentul dat.
     */
    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    /**
     * Indică dacă tokenul a fost deja folosit sau anulat.
     */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * Marchează tokenul ca folosit, ca să nu mai poată fi acceptat.
     */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    /**
     * Returnează utilizatorul căruia îi aparține tokenul.
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * Returnează hash-ul tokenului.
     */
    public String getTokenHash() {
        return tokenHash;
    }

    /**
     * Returnează momentul expirării.
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Returnează momentul revocării sau null dacă tokenul e încă valabil.
     */
    public Instant getRevokedAt() {
        return revokedAt;
    }
}
