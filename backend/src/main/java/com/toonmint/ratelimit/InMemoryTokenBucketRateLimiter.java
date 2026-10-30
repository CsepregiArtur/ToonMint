package com.toonmint.ratelimit;


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Token bucket ținut în memoria JVM. Folosit în dezvoltarea locală și ca
 * fallback când Redis nu este disponibil.
 */
public class InMemoryTokenBucketRateLimiter implements RateLimiter {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Aplică token bucket-ul local, sincronizat per cheie.
     */
    @Override
    public Decision acquire(String key, int capacity, double refillPerSecond, int requestedTokens) {
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(capacity));
        synchronized (bucket) {
            long now = System.nanoTime();
            double elapsedSeconds = Math.max(0, now - bucket.lastRefillNanos) / 1_000_000_000.0;
            bucket.tokens = Math.min(capacity, bucket.tokens + elapsedSeconds * refillPerSecond);
            bucket.lastRefillNanos = now;

            if (bucket.tokens >= requestedTokens) {
                bucket.tokens -= requestedTokens;
                return new Decision(true, (long) Math.floor(bucket.tokens), 0);
            }
            double missing = requestedTokens - bucket.tokens;
            long waitSeconds = (long) Math.ceil(missing / Math.max(refillPerSecond, 0.0001));
            return new Decision(false, (long) Math.floor(bucket.tokens), Math.max(1, waitSeconds));
        }
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefillNanos;

        private Bucket(int capacity) {
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
        }
    }
}
