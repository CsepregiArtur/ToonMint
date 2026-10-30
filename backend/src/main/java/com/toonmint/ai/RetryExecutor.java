package com.toonmint.ai;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Backoff exponențial cu jitter pentru apelurile către servicii externe.
 * Întârzierea pentru încercarea n este {@code min(initial * 2^(n-1), max)} plus
 * până la 50% jitter.
 */
public final class RetryExecutor {

    private final SecureRandom random = new SecureRandom();

    /**
     * Execută acțiunea cu backoff exponențial și jitter, până la numărul maxim de încercări.
     */
    public <T> T execute(String operation,
                         Supplier<T> action,
                         int maxAttempts,
                         Duration initialBackoff,
                         Duration maxBackoff) {
        RuntimeException last = null;
        int attempts = Math.max(1, maxAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return action.get();
            } catch (AiGenerationException ex) {
                last = ex;
                if (!ex.isRetryable() || attempt == attempts) {
                    break;
                }
            } catch (RuntimeException ex) {
                last = ex;
                if (attempt == attempts) {
                    break;
                }
            }
            sleep(backoffFor(attempt, initialBackoff, maxBackoff), operation);
        }
        throw last == null ? new AiGenerationException(operation + " failed", false) : last;
    }

    /**
     * Calculează întârzierea pentru o încercare, cu jitter de până la 50%.
     */
    private long backoffFor(int attempt, Duration initialBackoff, Duration maxBackoff) {
        long base = Math.min(initialBackoff.toMillis() * (1L << Math.min(attempt - 1, 20)), maxBackoff.toMillis());
        long jitter = (long) (random.nextDouble() * base * 0.5);
        return base + jitter;
    }

    /**
     * Așteaptă întârzierea calculată, păstrând flag-ul de întrerupere.
     */
    private void sleep(long millis, String operation) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiGenerationException(operation + " interrupted while backing off", false, ex);
        }
    }
}
