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

/**
 * Jurnalul brut al evenimentelor RevenueCat. Coloana unică {@code event_id} este
 * cheia de idempotență: RevenueCat retrimite evenimente, deci fiecare trebuie
 * aplicat o singură dată.
 */
@Entity
@Table(name = "subscription_events")
public class SubscriptionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "product_id", length = 100)
    private String productId;

    @Column(name = "app_user_id", length = 255)
    private String appUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventProcessingStatus status = EventProcessingStatus.IGNORED;

    @Column
    private String message;

    @Column(columnDefinition = "text")
    private String payload;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    /**
     * Constructor cerut de JPA.
     */
    protected SubscriptionEvent() {
    }

    /**
     * Creează înregistrarea brută a unui webhook RevenueCat.
     */
    public SubscriptionEvent(String eventId, String eventType, String productId, String appUserId, String payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.productId = productId;
        this.appUserId = appUserId;
        this.payload = payload;
    }

    /**
     * Setează momentul primirii evenimentului.
     */
    @PrePersist
    void onCreate() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }

    /**
     * Salvează rezultatul procesării evenimentului.
     */
    public void mark(EventProcessingStatus status, String message, User user) {
        this.status = status;
        this.message = message;
        this.user = user;
        this.processedAt = Instant.now();
    }

    /**
     * Returnează identificatorul înregistrării.
     */
    public Long getId() {
        return id;
    }

    /**
     * Returnează id-ul evenimentului RevenueCat (cheie de idempotență).
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Returnează utilizatorul identificat, dacă există.
     */
    public User getUser() {
        return user;
    }

    /**
     * Returnează tipul evenimentului.
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * Returnează id-ul produsului.
     */
    public String getProductId() {
        return productId;
    }

    /**
     * Returnează app_user_id trimis de RevenueCat.
     */
    public String getAppUserId() {
        return appUserId;
    }

    /**
     * Returnează starea procesării.
     */
    public EventProcessingStatus getStatus() {
        return status;
    }

    /**
     * Returnează mesajul explicativ al procesării.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returnează payload-ul brut primit.
     */
    public String getPayload() {
        return payload;
    }

    /**
     * Returnează momentul primirii.
     */
    public Instant getReceivedAt() {
        return receivedAt;
    }

    /**
     * Returnează momentul procesării.
     */
    public Instant getProcessedAt() {
        return processedAt;
    }
}
