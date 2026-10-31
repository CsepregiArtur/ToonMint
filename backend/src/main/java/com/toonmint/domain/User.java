package com.toonmint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "credit_balance", nullable = false)
    private int creditBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_type", nullable = false)
    private SubscriptionType subscriptionType = SubscriptionType.FREE;

    @Column(name = "subscription_expires_at")
    private Instant subscriptionExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    /** Versiunea termenilor acceptată la înregistrare (dovada consimțământului, GDPR art. 7(1)). */
    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    /** Setat când contul a fost șters la cerere și anonimizat (păstrăm dovezile fiscale). */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Constructor cerut de JPA.
     */
    protected User() {
    }

    /**
     * Creează un utilizator nou cu email, hash de parolă și nume afișat.
     */
    public User(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    /**
     * Inițializează identificatorul public și timestamp-urile înainte de inserare.
     */
    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    /**
     * Actualizează timestamp-ul de modificare înainte de update.
     */
    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Verifică dacă abonamentul este plătit și încă valabil la momentul dat.
     */
    public boolean isSubscriptionActive(Instant now) {
        return subscriptionType != SubscriptionType.FREE
                && subscriptionExpiresAt != null
                && subscriptionExpiresAt.isAfter(now);
    }

    /**
     * Returnează cheia primară internă.
     */
    public Long getId() {
        return id;
    }

    /**
     * Returnează identificatorul public folosit ca app_user_id în RevenueCat.
     */
    public UUID getPublicId() {
        return publicId;
    }

    /**
     * Returnează adresa de email.
     */
    public String getEmail() {
        return email;
    }

    /**
     * Setează adresa de email.
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Returnează hash-ul parolei.
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Setează hash-ul parolei.
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Returnează numele afișat.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Setează numele afișat.
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Indică dacă adresa de email a fost confirmată printr-un cod OTP.
     */
    public boolean isEmailVerified() {
        return emailVerified;
    }

    /**
     * Marchează adresa de email ca verificată și reține momentul confirmării.
     */
    public void markEmailVerified() {
        this.emailVerified = true;
        if (this.emailVerifiedAt == null) {
            this.emailVerifiedAt = Instant.now();
        }
    }

    /**
     * Returnează momentul confirmării adresei de email.
     */
    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    /**
     * Returnează soldul curent de credite.
     */
    public int getCreditBalance() {
        return creditBalance;
    }

    /**
     * Setează soldul de credite (folosit sub lock-ul pessimistic).
     */
    public void setCreditBalance(int creditBalance) {
        this.creditBalance = creditBalance;
    }

    /**
     * Returnează tipul abonamentului.
     */
    public SubscriptionType getSubscriptionType() {
        return subscriptionType;
    }

    /**
     * Setează tipul abonamentului.
     */
    public void setSubscriptionType(SubscriptionType subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    /**
     * Returnează momentul expirării abonamentului.
     */
    public Instant getSubscriptionExpiresAt() {
        return subscriptionExpiresAt;
    }

    /**
     * Setează momentul expirării abonamentului.
     */
    public void setSubscriptionExpiresAt(Instant subscriptionExpiresAt) {
        this.subscriptionExpiresAt = subscriptionExpiresAt;
    }

    /**
     * Returnează rolul utilizatorului (USER sau ADMIN).
     */
    public UserRole getRole() {
        return role;
    }

    /**
     * Setează rolul utilizatorului.
     */
    public void setRole(UserRole role) {
        this.role = role;
    }

    /**
     * Returnează data creării contului.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returnează data ultimei modificări.
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Reține versiunea termenilor acceptată și momentul acceptării.
     */
    public void acceptTerms(String version, Instant when) {
        this.termsVersion = version;
        this.termsAcceptedAt = when;
    }

    /**
     * Returnează versiunea termenilor acceptată (poate fi null pentru conturile vechi).
     */
    public String getTermsVersion() {
        return termsVersion;
    }

    /**
     * Returnează momentul acceptării termenilor.
     */
    public Instant getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    /**
     * Indică dacă datele au fost deja anonimizate la cererea utilizatorului.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Returnează momentul ștergerii contului.
     */
    public Instant getDeletedAt() {
        return deletedAt;
    }

    /**
     * Anonimizează contul păstrând dovezile contabile.
     *
     * <p>Emailul devine unic dar neutru (cheia tehnică a rândului rămâne pentru
     * tranzacții), parola este înlocuită cu un hash aleatoriu, iar datele de profil
     * și creditele sunt golite. Rândul din `credit_transactions` rămâne, pentru că
     * documentele fiscale se păstrează 10 ani.
     */
    public void anonymize(String anonymousEmail, String randomPasswordHash, Instant when) {
        this.email = anonymousEmail;
        this.passwordHash = randomPasswordHash;
        this.displayName = null;
        this.emailVerified = false;
        this.emailVerifiedAt = null;
        this.creditBalance = 0;
        this.subscriptionType = SubscriptionType.FREE;
        this.subscriptionExpiresAt = null;
        this.deletedAt = when;
    }
}
