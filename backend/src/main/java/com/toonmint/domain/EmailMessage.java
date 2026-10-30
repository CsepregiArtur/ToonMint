package com.toonmint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "email_messages")
public class EmailMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by_user_id")
    private User sentBy;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Constructor cerut de JPA.
     */
    protected EmailMessage() {
    }

    /**
     * Creează o înregistrare pentru un email trimis către utilizatori.
     */
    public EmailMessage(User sentBy, int recipientCount, String subject, String body, String status, String errorMessage) {
        this.sentBy = sentBy;
        this.recipientCount = recipientCount;
        this.subject = subject;
        this.body = body;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    /**
     * Setează momentul trimiterii.
     */
    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Returnează identificatorul emailului.
     */
    public Long getId() {
        return id;
    }

    /**
     * Returnează operatorul care a trimis emailul.
     */
    public User getSentBy() {
        return sentBy;
    }

    /**
     * Returnează numărul de destinatari.
     */
    public int getRecipientCount() {
        return recipientCount;
    }

    /**
     * Returnează subiectul emailului.
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Returnează conținutul emailului.
     */
    public String getBody() {
        return body;
    }

    /**
     * Returnează starea trimiterii (SENT/PARTIAL/FAILED/SIMULATED).
     */
    public String getStatus() {
        return status;
    }

    /**
     * Returnează detaliile eșecurilor, dacă există.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Returnează momentul trimiterii.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
