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
}
