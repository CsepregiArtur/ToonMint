package com.toonmint.web.dto;

import com.toonmint.domain.Job;
import com.toonmint.domain.JobStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Datele publice ale unui job de generare.
 */
public record JobResponse(
        Long id,
        UUID publicId,
        Long userId,
        String styleId,
        String styleLabel,
        JobStatus status,
        int creditsConsumed,
        int attempts,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt) {

    /**
     * Construiește DTO-ul din entitate și eticheta stilului.
     */
    public static JobResponse from(Job job, String styleLabel) {
        return new JobResponse(
                job.getId(),
                job.getPublicId(),
                job.getUser().getId(),
                job.getStyleId(),
                styleLabel,
                job.getStatus(),
                job.getCreditsConsumed(),
                job.getAttempts(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt());
    }
}
