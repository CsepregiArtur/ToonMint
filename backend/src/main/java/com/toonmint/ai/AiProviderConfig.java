package com.toonmint.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toonmint.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Alege providerul de imagini în funcție de configurație.
 *
 * <ul>
 *   <li>{@code gemini} — cere obligatoriu cheia API;</li>
 *   <li>{@code stub} — offline, fără cost (pentru UI fără cheie);</li>
 *   <li>{@code auto} (implicit) — Gemini dacă există {@code GEMINI_API_KEY}, altfel stub,
 *       cu un avertisment clar în log.</li>
 * </ul>
 */
@Configuration
public class AiProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(AiProviderConfig.class);

    @Bean
    public ImageGenerationProvider imageGenerationProvider(AppProperties properties, ObjectMapper objectMapper) {
        AppProperties.Ai ai = properties.ai();
        boolean hasKey = ai.apiKey() != null && !ai.apiKey().isBlank();
        String configured = ai.provider() == null ? "auto" : ai.provider().trim().toLowerCase();

        boolean useGemini = switch (configured) {
            case "gemini" -> {
                if (!hasKey) {
                    throw new IllegalStateException(
                            "AI_PROVIDER=gemini cere GEMINI_API_KEY (pune cheia în .env sau în variabilele de mediu).");
                }
                yield true;
            }
            case "stub" -> {
                log.warn("AI_PROVIDER=stub: imaginile sunt generate offline, nu cu Gemini.");
                yield false;
            }
            default -> {
                if (!hasKey) {
                    log.warn("GEMINI_API_KEY lipsește: folosesc providerul offline 'stub'. "
                            + "Adaugă cheia în .env pentru generări reale.");
                }
                yield hasKey;
            }
        };

        return useGemini
                ? new GeminiImageProvider(properties, objectMapper)
                : new StubImageProvider(properties);
    }
}
