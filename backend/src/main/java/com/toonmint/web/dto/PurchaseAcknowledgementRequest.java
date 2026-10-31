package com.toonmint.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Confirmarea cerută înainte de checkout: livrare imediată + renunțare la dreptul
 * de retragere (OUG 34/2014 art. 16 lit. m).
 */
public record PurchaseAcknowledgementRequest(
        @NotBlank @Size(max = 100) String productId,
        @NotBlank @Size(max = 20) String termsVersion) {
}
