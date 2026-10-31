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
 * Revendicarea unei generări gratuite. Emailul este unic, deci fiecare adresă
 * poate primi o singură imagine fără cont.
 */
@Entity
@Table(name = "guest_trials")
public class GuestTrial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GuestTrial() {
    }

    public GuestTrial(String email, Long jobId) {
        this.email = email;
        this.jobId = jobId;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Long getJobId() {
        return jobId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
