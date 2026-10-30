package com.toonmint.ratelimit;

public interface RateLimiter {

    /**
     * Consumă tokenuri din bucket și raportează dacă cererea este permisă.
     */
    Decision acquire(String key, int capacity, double refillPerSecond, int requestedTokens);

    /**
     * Rezultatul unei încercări de consum: permis, rămas, retry-after.
     */
    record Decision(boolean allowed, long remaining, long retryAfterSeconds) {
    }
}
