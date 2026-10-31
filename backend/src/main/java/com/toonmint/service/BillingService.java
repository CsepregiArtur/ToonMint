package com.toonmint.service;

import com.toonmint.config.AppProperties;
import com.toonmint.domain.PurchaseAcknowledgement;
import com.toonmint.domain.User;
import com.toonmint.repository.PurchaseAcknowledgementRepository;
import com.toonmint.security.AuthenticatedUser;
import com.toonmint.web.dto.BillingCatalogResponse;
import com.toonmint.web.dto.CreditPackResponse;
import com.toonmint.web.dto.PurchaseAcknowledgementRequest;
import com.toonmint.web.dto.PurchaseAcknowledgementResponse;
import com.toonmint.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Linkurile RevenueCat Web Billing. RevenueCat deține checkout-ul și bonul;
 * backendul reacționează doar la webhook-ul trimis după plată.
 */
@Service
public class BillingService {

    private final AppProperties properties;
    private final UserService userService;
    private final PurchaseAcknowledgementRepository acknowledgements;

    /** Cât timp o confirmare dată la checkout rămâne valabilă. */
    private static final Duration ACKNOWLEDGEMENT_VALIDITY = Duration.ofMinutes(30);

    /**
     * Injectează configurația RevenueCat și serviciul de utilizatori.
     */
    public BillingService(AppProperties properties,
                          UserService userService,
                          PurchaseAcknowledgementRepository acknowledgements) {
        this.properties = properties;
        this.userService = userService;
        this.acknowledgements = acknowledgements;
    }

    /**
     * Salvează confirmarea dinaintea checkout-ului: livrare imediată + renunțare la
     * dreptul de retragere (OUG 34/2014 art. 16 lit. m).
     */
    @Transactional
    public PurchaseAcknowledgementResponse acknowledge(AuthenticatedUser currentUser,
                                                       PurchaseAcknowledgementRequest request,
                                                       String clientIp) {
        AppProperties.RevenueCat.Product product = requireProduct(request.productId());
        String currentVersion = properties.legal().termsVersion();
        if (!request.termsVersion().trim().equals(currentVersion)) {
            throw new ApiException(HttpStatus.CONFLICT, "TERMS_VERSION_OUTDATED",
                    "The legal documents have changed. Reload the page and accept the current version.",
                    Map.of("currentVersion", currentVersion));
        }
        User user = userService.requireUser(currentUser.id());
        PurchaseAcknowledgement saved = acknowledgements.save(new PurchaseAcknowledgement(
                user, product.id(), product.kind().name(), request.termsVersion(), clientIp));
        return new PurchaseAcknowledgementResponse(
                saved.getProductId(), saved.isImmediateDelivery(), saved.isWithdrawalWaived(),
                saved.getAcceptedAt());
    }

    /**
     * Returnează planurile și pachetele, cu linkuri de checkout personalizate.
     */
    public BillingCatalogResponse catalog(AuthenticatedUser currentUser) {
        User user = userService.requireUser(currentUser.id());
        List<CreditPackResponse> plans = properties.revenueCat().products().stream()
                .filter(product -> product.kind() == AppProperties.RevenueCat.ProductKind.SUBSCRIPTION)
                .map(product -> toResponse(product, user))
                .toList();
        List<CreditPackResponse> packs = properties.revenueCat().products().stream()
                .filter(product -> product.kind() == AppProperties.RevenueCat.ProductKind.CREDIT_PACK)
                .map(product -> toResponse(product, user))
                .toList();
        return new BillingCatalogResponse(isConfigured(), properties.revenueCat().entitlementId(), plans, packs);
    }

    /**
     * Generează linkul RevenueCat pentru un pachet de credite.
     *
     * <p>Cere o confirmare recentă de checkout: fără ea, consumatorul ar putea cere
     * rambursarea în 14 zile și după ce a consumat creditele.
     */
    @Transactional(readOnly = true)
    public CreditPackResponse purchaseCredits(AuthenticatedUser currentUser, String packId) {
        AppProperties.RevenueCat.Product product = requireProduct(packId);
        if (product.kind() != AppProperties.RevenueCat.ProductKind.CREDIT_PACK) {
            throw ApiException.badRequest("Product " + packId + " is not a credit pack");
        }
        if (!isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENTS_NOT_CONFIGURED",
                    "RevenueCat purchase links are not configured on this environment");
        }
        User user = userService.requireUser(currentUser.id());
        boolean acknowledged = acknowledgements.existsByUserIdAndProductIdAndAcceptedAtAfter(
                user.getId(), product.id(), Instant.now().minus(ACKNOWLEDGEMENT_VALIDITY));
        if (!acknowledged) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "ACKNOWLEDGEMENT_REQUIRED",
                    "The immediate-delivery acknowledgement is required before checkout");
        }
        return toResponse(product, user);
    }

    /**
     * Caută produsul configurat sau aruncă 404.
     */
    private AppProperties.RevenueCat.Product requireProduct(String productId) {
        return properties.revenueCat().products().stream()
                .filter(candidate -> candidate.id().equals(productId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Unknown product " + productId));
    }

    /**
     * Mapează un produs RevenueCat la DTO-ul de catalog.
     */
    private CreditPackResponse toResponse(AppProperties.RevenueCat.Product product, User user) {
        return new CreditPackResponse(
                product.id(),
                product.kind().name(),
                product.label(),
                product.price(),
                product.credits() == null ? 0 : product.credits(),
                isConfigured() ? buildLink(product, user.getPublicId().toString()) : null);
    }

    /**
     * Verifică dacă linkul de checkout este configurat.
     */
    private boolean isConfigured() {
        String base = properties.revenueCat().purchaseLinkBaseUrl();
        return base != null && !base.isBlank();
    }

    /**
     * Construiește URL-ul de checkout pentru un produs.
     *
     * <p>App user id ajunge în path, cum cere RevenueCat pentru identificarea
     * clientului (`https://pay.rev.cat/<token>/<app_user_id>`). Dacă linkul din
     * dashboard conține deja `{app_user_id}`, acesta este înlocuit; un link care
     * are deja `app_user_id=` este folosit ca atare. `package_id` preselectează
     * pachetul din offering, ca utilizatorul să nu ajungă pe alt produs.
     */
    String buildLink(AppProperties.RevenueCat.Product product, String appUserId) {
        String template = properties.revenueCat().purchaseLinkBaseUrl().trim();
        String encodedUser = URLEncoder.encode(appUserId, StandardCharsets.UTF_8);
        String url;
        if (template.contains("{app_user_id}")) {
            url = template.replace("{app_user_id}", encodedUser);
        } else if (template.contains("app_user_id=")) {
            url = template;
        } else {
            int queryStart = template.indexOf('?');
            String base = queryStart >= 0 ? template.substring(0, queryStart) : template;
            String query = queryStart >= 0 ? template.substring(queryStart) : "";
            url = (base.endsWith("/") ? base : base + "/") + encodedUser + query;
        }

        String packageId = product.packageId();
        if (packageId == null || packageId.isBlank()) {
            return url;
        }
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "package_id=" + URLEncoder.encode(packageId.trim(), StandardCharsets.UTF_8);
    }
}
