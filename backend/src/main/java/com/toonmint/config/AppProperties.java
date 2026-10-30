package com.toonmint.config;

import com.toonmint.domain.SubscriptionType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Configurația centrală a aplicației. Fiecare valoare poate fi suprascrisă prin
 * variabilele de mediu documentate în {@code .env.example}.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        Jobs jobs,
        RateLimit rateLimit,
        TempStore tempStore,
        Ai ai,
        Mail mail,
        RevenueCat revenueCat,
        Admin admin
) {

    /**
     * Configurația tokenului JWT: secretul de semnare și durata de valabilitate.
     */
    public record Jwt(String secret, Duration ttl) {
    }

    /**
     * Origini permise pentru cererile venite din browser.
     */
    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * Setările pipeline-ului de generare: thread-uri, limită de upload, stiluri disponibile.
     */
    public record Jobs(int threads,
                       int creditsPerJob,
                       long maxInputBytes,
                       int maxOutputDimension,
                       Map<String, Style> styles) {

        /**
         * Un stil de generare: eticheta afișată în UI și promptul trimis către AI.
         */
        public record Style(String label, String prompt) {
        }
    }

    /**
     * Setările rate limiting-ului: activare, backend (memory/redis) și cereri pe minut.
     */
    public record RateLimit(boolean enabled, String backend, int requestsPerMinute) {
    }

    /**
     * Cache-ul temporar de imagini: backend, TTL și numărul maxim de intrări.
     */
    public record TempStore(String backend, Duration ttl, long maxEntries) {
    }

    /**
     * Configurația providerului de imagini: Gemini sau stub, model, retry și timeout.
     */
    public record Ai(String provider,
                     String apiKey,
                     String model,
                     String baseUrl,
                     int maxAttempts,
                     Duration initialBackoff,
                     Duration maxBackoff,
                     Duration timeout) {
    }

    /**
     * Setările de email: activare, expeditor și numele afișat.
     */
    public record Mail(boolean enabled, String from, String fromName) {
    }

    /**
     * Contul de operator creat automat la primul start (opțional).
     */
    public record Admin(String email, String password) {
    }

    /**
     * Integrarea RevenueCat: token de webhook, link de checkout și catalogul de produse.
     */
    public record RevenueCat(String webhookAuthToken,
                             String purchaseLinkBaseUrl,
                             String entitlementId,
                             List<Product> products) {

        /**
         * Tipul produsului vândut prin RevenueCat.
         */
        public enum ProductKind {
            SUBSCRIPTION,
            CREDIT_PACK
        }

        /**
         * Un produs RevenueCat mapat pe credite și/sau tip de abonament.
         */
        public record Product(String id,
                              ProductKind kind,
                              SubscriptionType subscriptionType,
                              Integer credits,
                              String label,
                              String price) {
        }
    }
}
