package com.toonmint.config;

import com.toonmint.domain.SubscriptionType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Configurația centrală a aplicației. Fiecare valoare poate fi suprascrisă prin
 * variabilele de mediu documentate în {@code .env.example}.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Legal legal,
        Cors cors,
        Jobs jobs,
        RateLimit rateLimit,
        TempStore tempStore,
        Ai ai,
        Mail mail,
        RevenueCat revenueCat,
        Admin admin,
        Demo demo,
        Finance finance
) {

    /**
     * Configurația tokenului JWT: secretul de semnare, durata de valabilitate a
     * tokenului de acces și durata tokenului de refresh.
     */
    public record Jwt(String secret, Duration ttl, Duration refreshTtl) {
    }

    /**
     * Versiunea documentelor legale acceptată de aplicație. Se compară cu versiunea
     * trimisă de client, ca să nu se poată înregistra un consimțământ pentru un text
     * care nu mai este publicat.
     */
    public record Legal(String termsVersion) {
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
                       boolean recoveryEnabled,
                       Duration maxProcessing,
                       String stylePreviewBaseUrl,
                       String stylePreviewOutputDir,
                       String stylePreviewSource,
                       long stylePreviewDelayMillis,
                       int stylePreviewMaxDimension,
                       int stylePreviewHeroDimension,
                       Map<String, Style> styles) {

        /**
         * Un stil de generare: eticheta afișată în UI și promptul trimis către AI.
         */
        /**
         * Un stil: eticheta din UI și promptul trimis către modelul de imagine.
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
     * Conturi demo pentru dezvoltare: login fără parolă, doar când `enabled` este true.
     * În producție rămâne dezactivat (profilul `prod` îl forțează pe false).
     */
    public record Demo(boolean enabled,
                       String userEmail,
                       String userPassword,
                       int userCredits,
                       String adminEmail,
                       String adminPassword,
                       int adminCredits) {
    }

    /**
     * Bookkeeping: moneda de raportare, costul AI per generare și cursurile de
     * schimb folosite pentru sumele încasate în alte monede.
     */
    public record Finance(String reportingCurrency,
                          BigDecimal costPerGeneration,
                          Map<String, BigDecimal> fxRates) {

        /**
         * Normalizează valorile lipsă, ca raportul să nu depindă de config complet.
         */
        public Finance {
            reportingCurrency = reportingCurrency == null || reportingCurrency.isBlank()
                    ? "USD" : reportingCurrency.trim().toUpperCase(Locale.ROOT);
            costPerGeneration = costPerGeneration == null
                    ? new BigDecimal("0.034") : costPerGeneration;
            fxRates = fxRates == null ? Map.of() : Map.copyOf(fxRates);
        }

        /**
         * Valorile implicite, folosite dacă grupul `app.finance` lipsește.
         */
        public static Finance defaults() {
            return new Finance(null, null, null);
        }
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
         *
         * <p>`packageId` este identificatorul pachetului din offering-ul RevenueCat;
         * dacă e completat, linkul de checkout deschide direct produsul respectiv.
         */
        public record Product(String id,
                              ProductKind kind,
                              SubscriptionType subscriptionType,
                              Integer credits,
                              String label,
                              String price,
                              String packageId) {
        }
    }
}
