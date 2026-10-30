package com.toonmint.ai;

public class AiGenerationException extends RuntimeException {

    private final boolean retryable;

    /**
     * Creează excepția cu indicarea posibilității de reîncercare.
     */
    public AiGenerationException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    /**
     * Creează excepția păstrând cauza originală.
     */
    public AiGenerationException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    /**
     * Indică dacă apelul poate fi reîncercat.
     */
    public boolean isRetryable() {
        return retryable;
    }
}
