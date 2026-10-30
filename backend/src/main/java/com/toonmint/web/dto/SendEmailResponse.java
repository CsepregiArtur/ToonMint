package com.toonmint.web.dto;

import java.util.List;

/**
 * Rezultatul trimiterii: câți destinatari, câți au primit mesajul și erorile.
 */
public record SendEmailResponse(int requested, int sent, int failed, boolean simulated, List<String> failures) {
}
