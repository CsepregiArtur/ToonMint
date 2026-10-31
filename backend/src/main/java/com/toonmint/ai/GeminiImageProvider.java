package com.toonmint.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toonmint.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generare de imagini prin Google Gemini (familia Nano Banana). Reîncercările
 * sunt gestionate de {@link RetryExecutor} cu backoff exponențial și jitter.
 */
public class GeminiImageProvider implements ImageGenerationProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiImageProvider.class);

    private final AppProperties.Ai config;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final RetryExecutor retryExecutor = new RetryExecutor();

    /**
     * Validează cheia API și construiește clientul HTTP către Gemini.
     */
    public GeminiImageProvider(AppProperties properties, ObjectMapper objectMapper) {
        this.config = properties.ai();
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY is required when AI_PROVIDER=gemini");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(config.timeout());
        this.restClient = RestClient.builder()
                .baseUrl(config.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
        log.info("Using Gemini image provider with model {}", config.model());
    }

    /**
     * Apelează Gemini cu retry exponențial și întoarce imaginea generată.
     */
    @Override
    public GeneratedImage generate(byte[] source, String sourceMimeType, String prompt) {
        return retryExecutor.execute(
                "gemini image generation",
                () -> callGemini(source, sourceMimeType, prompt),
                config.maxAttempts(),
                config.initialBackoff(),
                config.maxBackoff());
    }

    /**
     * Numele providerului și modelul folosit.
     */
    @Override
    public String name() {
        return "gemini:" + config.model();
    }

    /**
     * Trimite cererea către API și traduge erorile în excepții retryable.
     */
    private GeneratedImage callGemini(byte[] source, String sourceMimeType, String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(
                        Map.of("text", prompt),
                        Map.of("inlineData", Map.of(
                                "mimeType", sourceMimeType,
                                "data", Base64.getEncoder().encodeToString(source)))))));
        body.put("generationConfig", Map.of("responseModalities", List.of("IMAGE")));

        String uri = "/v1beta/models/" + config.model() + ":generateContent?key={apiKey}";
        try {
            String response = restClient.post()
                    .uri(uri, config.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parse(response);
        } catch (HttpStatusCodeException ex) {
            int status = ex.getStatusCode().value();
            String errorBody = ex.getResponseBodyAsString();
            boolean dailyQuota = errorBody.contains("PerDay") || errorBody.contains("per day");
            boolean retryable = (status == 429 && !dailyQuota) || status >= 500;
            log.warn("Gemini call failed with HTTP {}: {}", status, truncate(errorBody));
            throw new AiGenerationException(describeFailure(status, errorBody, dailyQuota), retryable, ex);
        } catch (ResourceAccessException ex) {
            log.warn("Gemini call failed: {}", ex.getMessage());
            throw new AiGenerationException("Gemini API is unreachable", true, ex);
        }
    }

    /**
     * Extrage imaginea base64 din răspunsul Gemini.
     */
    /**
     * Traduce răspunsul de eroare al Gemini într-un mesaj acționabil pentru operator.
     */
    private String describeFailure(int status, String body, boolean dailyQuota) {
        if (dailyQuota) {
            return "Cota zilnică Gemini este epuizată (" + status + "). "
                    + "Activează facturarea în Google AI Studio pentru tier-ul plătit "
                    + "sau încearcă din nou după resetarea cotei.";
        }
        if (status == 429) {
            return "Gemini a limitat temporar cererile (HTTP 429). Reîncerc automat.";
        }
        if (status == 400 || status == 403) {
            return "Gemini a respins cererea (HTTP " + status + "). Verifică GEMINI_API_KEY, "
                    + "modelul și permisiunile cheii. Detalii: " + truncate(body);
        }
        return "Gemini API a răspuns cu eroare HTTP " + status + ".";
    }

    /**
     * Scurtează răspunsul de eroare pentru log.
     */
    private String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 400 ? body.substring(0, 400) + "…" : body;
    }

    private GeneratedImage parse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray()) {
                for (JsonNode candidate : candidates) {
                    for (JsonNode part : candidate.path("content").path("parts")) {
                        JsonNode inline = part.path("inlineData");
                        if (inline.hasNonNull("data")) {
                            String mimeType = inline.path("mimeType").asText("image/png");
                            return new GeneratedImage(
                                    Base64.getDecoder().decode(inline.get("data").asText()),
                                    mimeType);
                        }
                    }
                }
            }
            JsonNode feedback = root.path("promptFeedback").path("blockReason");
            if (feedback.isTextual()) {
                throw new AiGenerationException("Prompt was blocked by the provider: " + feedback.asText(), false);
            }
            throw new AiGenerationException("Gemini returned no image", false);
        } catch (AiGenerationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiGenerationException("Could not parse the Gemini response", false, ex);
        }
    }
}
