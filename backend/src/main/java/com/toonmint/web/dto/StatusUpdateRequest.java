package com.toonmint.web.dto;

import com.toonmint.domain.JobStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Cerere de schimbare a statusului unui job.
 */
public record StatusUpdateRequest(@NotNull JobStatus status) {
}
