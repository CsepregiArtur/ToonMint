package com.toonmint.service;

/**
 * Eveniment publicat după crearea jobului, procesat după commit.
 */
public record JobQueuedEvent(Long jobId) {
}
