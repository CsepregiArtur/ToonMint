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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aplică evenimentele RevenueCat. Fiecare event id este salvat o singură dată,
 * deci retrimiterile RevenueCat nu pot acorda credite de două ori.
 */
@Service
public class RevenueCatService {

    private static final Logger log = LoggerFactory.getLogger(RevenueCatService.class);

    private static final Pattern PRICE_PATTERN = Pattern.compile("(\\d+(?:[.,]\\d+)?)");

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final SubscriptionEventRepository eventRepository;
    private final CreditService creditService;
    private final FinanceService financeService;
    private final AppProperties properties;
    private final TransactionTemplate transactions;

    /**
     * Injectează parserul JSON, repository-urile și serviciile de credite și financiar.
     */
    public RevenueCatService(ObjectMapper objectMapper,
                             UserRepository userRepository,
                             SubscriptionEventRepository eventRepository,
                             CreditService creditService,
                             FinanceService financeService,
                             AppProperties properties,
                             PlatformTransactionManager transactionManager) {
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.creditService = creditService;
        this.financeService = financeService;
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
                recordPurchase(event, product);
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
                recordPurchase(event, product);
                return new Outcome(EventProcessingStatus.PROCESSED,
                        granted > 0 ? "Granted " + granted + " credits" : "Credits were already granted", user);
            }
            case "EXPIRATION" -> {
                user.setSubscriptionType(SubscriptionType.FREE);
                user.setSubscriptionExpiresAt(null);
                return new Outcome(EventProcessingStatus.PROCESSED, "Subscription expired", user);
            }
            case "REFUND" -> {
                recordRefund(event, product);
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
     * Înregistrează încasarea și comisionul magazinului pentru o plată confirmată.
     * Lipsa prețului nu blochează plata: doar se sare peste linia de venit.
     */
    private void recordPurchase(ParsedEvent event, AppProperties.RevenueCat.Product product) {
        BigDecimal price = resolvePrice(event, product);
        if (price == null) {
            log.warn("RevenueCat event {} has no price; the sale was not recorded in the ledger",
                    event.eventId());
            return;
        }
        Instant occurredAt = event.purchasedAt() == null ? Instant.now() : event.purchasedAt();
        financeService.recordSale(event.eventId(), product.id(), price, event.currency(), occurredAt);

        BigDecimal takehome = event.takehomePercentage();
        if (takehome != null && takehome.signum() > 0 && takehome.compareTo(BigDecimal.ONE) < 0) {
            BigDecimal fee = price.subtract(price.multiply(takehome)).setScale(4, RoundingMode.HALF_UP);
            financeService.recordStoreFee(event.eventId(), product.id(), fee, event.currency(), occurredAt);
        }
    }

    /**
     * Înregistrează o rambursare ca cheltuială, ca să scadă din încasări.
     */
    private void recordRefund(ParsedEvent event, AppProperties.RevenueCat.Product product) {
        BigDecimal price = resolvePrice(event, product);
        if (price == null) {
            log.warn("RevenueCat event {} has no price; the refund was not recorded in the ledger",
                    event.eventId());
            return;
        }
        financeService.recordRefund(event.eventId(), product.id(), price, event.currency(),
                event.purchasedAt() == null ? Instant.now() : event.purchasedAt());
    }

    /**
     * Ia suma din payload sau, în lipsa ei, din eticheta de preț configurată.
     */
    private BigDecimal resolvePrice(ParsedEvent event, AppProperties.RevenueCat.Product product) {
        return event.price() != null ? event.price() : parsePriceLabel(product.price());
    }

    /**
     * Extrage prima sumă dintr-o etichetă de preț (ex. {@code "$4.99 / week"} → 4.99).
     */
    static BigDecimal parsePriceLabel(String label) {
        if (label == null) {
            return null;
        }
        Matcher matcher = PRICE_PATTERN.matcher(label);
        return matcher.find() ? new BigDecimal(matcher.group(1).replace(',', '.')) : null;
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
                    epochMillis(event, "expiration_at_ms"),
                    epochMillis(event, "purchased_at_ms"),
                    decimal(event, "price", "price_in_purchased_currency"),
                    text(event, "currency"),
                    decimal(event, "takehome_percentage"));
        } catch (IOException ex) {
            throw ApiException.badRequest("Webhook payload is not valid JSON");
        }
    }

    /**
     * Citește primul câmp numeric disponibil dintr-o listă de nume.
     */
    private BigDecimal decimal(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) {
                return value.decimalValue();
            }
            if (value.isTextual() && !value.asText().isBlank()) {
                try {
                    return new BigDecimal(value.asText().trim());
                } catch (NumberFormatException ignored) {
                    // câmpul nu este numeric; încercăm următorul
                }
            }
        }
        return null;
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
                               Instant expirationAt,
                               Instant purchasedAt,
                               BigDecimal price,
                               String currency,
                               BigDecimal takehomePercentage) {
    }

    /**
     * Rezultatul aplicării unui eveniment.
     */
    private record Outcome(EventProcessingStatus status, String message, User user) {
    }
}
