package com.toonmint.ratelimit;

import com.toonmint.config.AppProperties;
import com.toonmint.web.error.RateLimitExceededException;

public class RateLimitService {

    private final RateLimiter limiter;
    private final AppProperties properties;

    /**
     * Injectează limiter-ul ales și configurația.
     */
    public RateLimitService(RateLimiter limiter, AppProperties properties) {
        this.limiter = limiter;
        this.properties = properties;
    }

    /**
     * @throws RateLimitExceededException când apelantul a depășit bugetul de
     *                                    cereri pe minut configurat.
     */
    public void check(Long userId) {
        check("user:" + userId);
    }

    /**
     * Verifică limita pentru o cheie arbitrară (email, IP) — folosită de fluxul de trial.
     */
    public void check(String key) {
        if (!properties.rateLimit().enabled()) {
            return;
        }
        int perMinute = Math.max(1, properties.rateLimit().requestsPerMinute());
        double refillPerSecond = perMinute / 60.0;
        RateLimiter.Decision decision = limiter.acquire(key, perMinute, refillPerSecond, 1);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfterSeconds(), perMinute);
        }
    }
}
