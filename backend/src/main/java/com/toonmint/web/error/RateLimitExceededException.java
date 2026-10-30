package com.toonmint.web.error;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class RateLimitExceededException extends ApiException {

    private final long retryAfterSeconds;

    /**
     * Creează eroarea 429 cu timpul recomandat de reîncercare.
     */
    public RateLimitExceededException(long retryAfterSeconds, int requestsPerMinute) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                "Too many requests. Please retry later.",
                Map.of("retryAfterSeconds", retryAfterSeconds, "requestsPerMinute", requestsPerMinute));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Returnează numărul de secunde până la următoarea încercare permisă.
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
