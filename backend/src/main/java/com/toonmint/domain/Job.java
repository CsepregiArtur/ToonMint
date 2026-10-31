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
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "guest_email")
    private String guestEmail;

    @Column(nullable = false)
    private boolean trial;

    @Column(name = "style_id", nullable = false)
    private String styleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "credits_consumed", nullable = false)
    private int creditsConsumed = 1;

    @Column(name = "input_mime_type")
    private String inputMimeType;

    @Column(name = "input_size_bytes")
    private Long inputSizeBytes;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Constructor cerut de JPA.
     */
    protected Job() {
    }

    /**
     * Creează un job nou pentru utilizatorul și stilul dat.
     */
    public Job(User user, String styleId, int creditsConsumed) {
        this.user = user;
        this.styleId = styleId;
        this.creditsConsumed = creditsConsumed;
    }

    /**
     * Creează un job de trial: fără cont, fără consum de credite, legat de email.
     */
    public static Job forGuest(String email, String styleId) {
        Job job = new Job(null, styleId, 0);
        job.guestEmail = email;
        job.trial = true;
        return job;
    }

    /**
     * Inițializează identificatorul public și timestamp-ul de creare.
     */
    @PrePersist
    void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Marchează jobul ca fiind preluat de worker și incrementează numărul de încercări.
     */
    public void markProcessing() {
        status = JobStatus.PROCESSING;
        startedAt = Instant.now();
        errorMessage = null;
        attempts++;
    }

    /**
     * Marchează jobul ca finalizat și salvează momentul terminării.
     */
    public void markCompleted() {
        status = JobStatus.COMPLETED;
        completedAt = Instant.now();
        errorMessage = null;
    }

    /**
     * Marchează jobul ca eșuat și păstrează motivul erorii.
     */
    public void markFailed(String reason) {
        status = JobStatus.FAILED;
        completedAt = Instant.now();
        errorMessage = reason;
    }

    /**
     * Marchează jobul ca șters logic.
     */
    public void markRemoved() {
        status = JobStatus.REMOVED;
        completedAt = Instant.now();
    }

    /**
     * Readuce jobul în starea PENDING pentru o nouă încercare.
     */
    public void resetForRetry() {
        status = JobStatus.PENDING;
        errorMessage = null;
        startedAt = null;
        completedAt = null;
    }

    /**
     * Returnează cheia primară a jobului.
     */
    public Long getId() {
        return id;
    }

    /**
     * Returnează identificatorul public al jobului.
     */
    public UUID getPublicId() {
        return publicId;
    }

    /**
     * Returnează proprietarul jobului; null pentru joburile de trial.
     */
    public User getUser() {
        return user;
    }

    /**
     * Email-ul vizitatorului, completat doar pentru joburile de trial.
     */
    public String getGuestEmail() {
        return guestEmail;
    }

    /**
     * Indică dacă jobul provine din generarea gratuită (fără cont).
     */
    public boolean isTrial() {
        return trial;
    }

    /**
     * Returnează identificatorul stilului ales.
     */
    public String getStyleId() {
        return styleId;
    }

    /**
     * Setează identificatorul stilului.
     */
    public void setStyleId(String styleId) {
        this.styleId = styleId;
    }

    /**
     * Returnează starea curentă a jobului.
     */
    public JobStatus getStatus() {
        return status;
    }

    /**
     * Setează starea jobului.
     */
    public void setStatus(JobStatus status) {
        this.status = status;
    }

    /**
     * Returnează numărul de credite consumate pentru acest job.
     */
    public int getCreditsConsumed() {
        return creditsConsumed;
    }

    /**
     * Setează numărul de credite consumate.
     */
    public void setCreditsConsumed(int creditsConsumed) {
        this.creditsConsumed = creditsConsumed;
    }

    /**
     * Returnează tipul MIME detectat al imaginii încărcate.
     */
    public String getInputMimeType() {
        return inputMimeType;
    }

    /**
     * Setează tipul MIME al imaginii încărcate.
     */
    public void setInputMimeType(String inputMimeType) {
        this.inputMimeType = inputMimeType;
    }

    /**
     * Returnează dimensiunea imaginii încărcate, în bytes.
     */
    public Long getInputSizeBytes() {
        return inputSizeBytes;
    }

    /**
     * Setează dimensiunea imaginii încărcate.
     */
    public void setInputSizeBytes(Long inputSizeBytes) {
        this.inputSizeBytes = inputSizeBytes;
    }

    /**
     * Returnează mesajul de eroare, dacă jobul a eșuat.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Returnează numărul de procesări pornite pentru acest job.
     */
    public int getAttempts() {
        return attempts;
    }

    /**
     * Returnează data creării jobului.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returnează momentul în care a început procesarea.
     */
    public Instant getStartedAt() {
        return startedAt;
    }

    /**
     * Returnează momentul finalizării sau eșecului.
     */
    public Instant getCompletedAt() {
        return completedAt;
    }
}
