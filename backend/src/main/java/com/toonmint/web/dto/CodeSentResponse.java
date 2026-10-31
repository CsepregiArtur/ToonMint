package com.toonmint.web.dto;

/**
 * Răspuns generic pentru cererile de cod: nu dezvăluie dacă adresa are cont.
 */
public record CodeSentResponse(boolean sent, long resendAvailableInSeconds) {
}
