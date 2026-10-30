package com.toonmint.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Statistici agregate pe interval și pe stil.
 */
public record JobStatsResponse(
        Instant from,
        Instant to,
        long totalJobs,
        long completed,
        long failed,
        double successRate,
        long creditsConsumed,
        List<StyleStats> byStyle) {

    public record StyleStats(String styleId, long jobs, long completed, long failed, long credits) {
    }
}
