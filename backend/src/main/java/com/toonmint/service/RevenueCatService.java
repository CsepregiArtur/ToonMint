package com.toonmint.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toonmint.config.AppProperties;
import com.toonmint.domain.CreditTransactionType;
import com.toonmint.domain.EventProcessingStatus;
import com.toonmint.domain.SubscriptionEvent;
import com.toonmint.domain.SubscriptionType;
import com.toonmint.domain.User;
import com.toonmint.repository.SubscriptionEventRepository;
import com.toonmint.repository.UserRepository;
import com.toonmint.web.dto.WebhookResponse;
import com.toonmint.web.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Aplică evenimentele RevenueCat. Fiecare event id este salvat o singură dată,
 * deci retrimiterile RevenueCat nu pot acorda credite de două ori.
 */
@Service
public class RevenueCatService {

    private static final Logger log = LoggerFactory.getLogger(RevenueCatService.class);

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final SubscriptionEventRepository eventRepository;
    private final CreditService creditService;
    private final AppProperties properties;
    private final TransactionTemplate transactions;

    /**
     * Injectează parserul JSON, repository-urile și serviciul de credite.
     */
    public RevenueCatService(ObjectMapper objectMapper,
                             UserRepository userRepository,
                             SubscriptionEventRepository eventRepository,
                             CreditService creditService,
                             AppProperties properties,
                             PlatformTransactionManager transactionManager) {
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.creditService = creditService;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Înregistrează evenimentul o singură dată și aplică efectele lui.
     */
    public WebhookResponse handle(String rawBody) {
        ParsedEvent event = parse(rawBody);
        SubscriptionEvent record = new SubscriptionEvent(
                event.eventId(), event.type(), event.productId(), event.appUserId(), truncate(rawBody));

        try {
            transactions.executeWithoutResult(status -> eventRepository.saveAndFlush(record));
        } catch (DataIntegrityViolationException ex) {
            return new WebhookResponse(EventProcessingStatus.DUPLICATE.name(), event.eventId(),
                    "Event already processed");
        }

        Outcome outcome;
        try {
            outcome = transactions.execute(status -> apply(event));
        } catch (RuntimeException ex) {
            log.error("Could not apply RevenueCat event {}", event.eventId(), ex);
            outcome = new Outcome(EventProcessingStatus.FAILED, "Processing failed: " + ex.getMessage(), null);
        }

        Outcome finalOutcome = outcome;
        transactions.executeWithoutResult(status -> eventRepository.findByEventId(event.eventId())
                .ifPresent(stored -> stored.mark(finalOutcome.status(), finalOutcome.message(), finalOutcome.user())));

        return new WebhookResponse(finalOutcome.status().name(), event.eventId(), finalOutcome.message());
    }

    /**
     * Aplică evenimentul: activează abonamentul sau acordă credite.
     */
    private Outcome apply(ParsedEvent event) {
        if ("TEST".equals(event.type())) {
            return new Outcome(EventProcessingStatus.IGNORED, "Test event received", null);
        }
        User user = resolveUser(event.appUserId());
        if (user == null) {
            return new Outcome(EventProcessingStatus.FAILED,
                    "Unknown app_user_id: " + event.appUserId(), null);
        }
        AppProperties.RevenueCat.Product product = findProduct(event.productId());
        if (product == null) {
            return new Outcome(EventProcessingStatus.IGNORED,
                    "Unknown product: " + event.productId(), user);
        }

        switch (event.type()) {
            case "INITIAL_PURCHASE", "RENEWAL", "PRODUCT_CHANGE" -> {
                if (product.kind() != AppProperties.RevenueCat.ProductKind.SUBSCRIPTION) {
                    return new Outcome(EventProcessingStatus.IGNORED, "Product is not a subscription", user);
                }
                activateSubscription(user, product, event.expirationAt());
                grantProductCredits(user, product, event.eventId(), CreditTransactionType.SUBSCRIPTION_GRANT,
                        "Subscription credits (" + product.label() + ")");
                return new Outcome(EventProcessingStatus.PROCESSED,
                        "Subscription " + product.subscriptionType() + " active until "
                                + user.getSubscriptionExpiresAt(), user);
            }
            case "UNCANCELLATION" -> {
                if (product.kind() != AppProperties.RevenueCat.ProductKind.SUBSCRIPTION) {
                    return new Outcome(EventProcessingStatus.IGNORED, "Product is not a subscription", user);
                }
                activateSubscription(user, product, event.expirationAt());
                return new Outcome(EventProcessingStatus.PROCESSED, "Subscription reactivated", user);
            }
            case "NON_RENEWING_PURCHASE" -> {
                if (product.kind() != AppProperties.RevenueCat.ProductKind.CREDIT_PACK) {
                    return new Outcome(EventProcessingStatus.IGNORED, "Product is not a credit pack", user);
                }
                int granted = grantProductCredits(user, product, event.eventId(), CreditTransactionType.PURCHASE,
                        "Credit pack purchase (" + product.label() + ")");
                return new Outcome(EventProcessingStatus.PROCESSED,
                        granted > 0 ? "Granted " + granted + " credits" : "Credits were already granted", user);
            }
            case "EXPIRATION" -> {
                user.setSubscriptionType(SubscriptionType.FREE);
                user.setSubscriptionExpiresAt(null);
                return new Outcome(EventProcessingStatus.PROCESSED, "Subscription expired", user);
            }
            case "REFUND" -> {
                if (product.kind() == AppProperties.RevenueCat.ProductKind.SUBSCRIPTION) {
                    user.setSubscriptionType(SubscriptionType.FREE);
                    user.setSubscriptionExpiresAt(null);
                    return new Outcome(EventProcessingStatus.PROCESSED,
                            "Subscription refunded and deactivated", user);
                }
                return new Outcome(EventProcessingStatus.IGNORED,
                        "Credit pack refunded; credits already granted are kept (review manually)", user);
            }
            case "CANCELLATION" -> {
                return new Outcome(EventProcessingStatus.IGNORED,
                        "Subscription cancelled; access continues until " + user.getSubscriptionExpiresAt(), user);
            }
            case "BILLING_ISSUE" -> {
                return new Outcome(EventProcessingStatus.IGNORED, "Billing issue reported by RevenueCat", user);
            }
            default -> {
                return new Outcome(EventProcessingStatus.IGNORED, "Unhandled event type " + event.type(), user);
            }
        }
    }

    /**
     * Setează tipul abonamentului și data de expirare.
     */
    private void activateSubscription(User user,
                                      AppProperties.RevenueCat.Product product,
                                      Instant expiration) {
        user.setSubscriptionType(product.subscriptionType());
        if (expiration != null) {
            user.setSubscriptionExpiresAt(expiration);
        } else {
            Duration period = product.subscriptionType() == SubscriptionType.YEARLY
                    ? Duration.ofDays(365) : Duration.ofDays(7);
            user.setSubscriptionExpiresAt(Instant.now().plus(period));
        }
    }

    /**
     * Acordă creditele produsului, idempotent pe event id.
     */
    private int grantProductCredits(User user,
                                    AppProperties.RevenueCat.Product product,
                                    String eventId,
                                    CreditTransactionType type,
                                    String description) {
        int credits = product.credits() == null ? 0 : product.credits();
        if (credits <= 0) {
            return 0;
        }
        boolean granted = creditService.grant(user.getId(), credits, type, "REVENUECAT", eventId, description);
        return granted ? credits : 0;
    }

    /**
     * Găsește utilizatorul după app_user_id.
     */
    private User resolveUser(String appUserId) {
        if (appUserId == null || appUserId.isBlank()) {
            return null;
        }
        try {
            return userRepository.findByPublicId(UUID.fromString(appUserId)).orElse(null);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Caută produsul în catalogul configurat.
     */
    private AppProperties.RevenueCat.Product findProduct(String productId) {
        if (productId == null || productId.isBlank()) {
            return null;
        }
        return properties.revenueCat().products().stream()
                .filter(product -> product.id().equals(productId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Extrage câmpurile relevante din payload-ul webhook-ului.
     */
    private ParsedEvent parse(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode event = root.path("event");
            String eventId = text(event, "id");
            String type = text(event, "type");
            if (eventId == null || type == null) {
                throw ApiException.badRequest("Webhook payload is missing event.id or event.type");
            }
            return new ParsedEvent(
                    eventId,
                    type,
                    text(event, "product_id"),
                    text(event, "app_user_id"),
                    epochMillis(event, "expiration_at_ms"));
        } catch (IOException ex) {
            throw ApiException.badRequest("Webhook payload is not valid JSON");
        }
    }

    /**
     * Citește un câmp text nenul din JSON.
     */
    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    /**
     * Transformă un timestamp în milisecunde într-un Instant.
     */
    private Instant epochMillis(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() && value.asLong() > 0 ? Instant.ofEpochMilli(value.asLong()) : null;
    }

    /**
     * Limitează payload-ul salvat în baza de date.
     */
    private String truncate(String payload) {
        return payload.length() > 20000 ? payload.substring(0, 20000) : payload;
    }

    /**
     * Câmpurile normalizate ale unui eveniment RevenueCat.
     */
    private record ParsedEvent(String eventId,
                               String type,
                               String productId,
                               String appUserId,
                               Instant expirationAt) {
    }

    /**
     * Rezultatul aplicării unui eveniment.
     */
    private record Outcome(EventProcessingStatus status, String message, User user) {
    }
}
