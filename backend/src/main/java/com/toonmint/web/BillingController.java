package com.toonmint.web;

import com.toonmint.security.AuthenticatedUser;
import com.toonmint.security.CurrentUser;
import com.toonmint.service.BillingService;
import com.toonmint.service.CreditQueryService;
import com.toonmint.web.dto.BillingCatalogResponse;
import com.toonmint.web.dto.CreditPackResponse;
import com.toonmint.web.dto.PageResponse;
import com.toonmint.web.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Credits & billing")
public class BillingController {

    private final BillingService billingService;
    private final CreditQueryService creditQueryService;
    private final CurrentUser currentUser;

    /**
     * Injectează serviciile de facturare și istoric credite.
     */
    public BillingController(BillingService billingService,
                             CreditQueryService creditQueryService,
                             CurrentUser currentUser) {
        this.billingService = billingService;
        this.creditQueryService = creditQueryService;
        this.currentUser = currentUser;
    }

    /**
     * Returnează planurile și pachetele disponibile.
     */
    @GetMapping("/billing/catalog")
    @Operation(summary = "Plans and credit packs with RevenueCat purchase links")
    public BillingCatalogResponse catalog() {
        return billingService.catalog(currentUser.require());
    }

    /**
     * Returnează doar pachetele de credite.
     */
    @GetMapping("/credits/packs")
    @Operation(summary = "Credit packs available for purchase")
    public List<CreditPackResponse> packs() {
        return billingService.catalog(currentUser.require()).packs();
    }

    /**
     * Creează linkul de checkout RevenueCat pentru un pachet.
     */
    @PostMapping("/credits/purchase")
    @Operation(summary = "Create a RevenueCat checkout link for a credit pack")
    public CreditPackResponse purchase(@Valid @RequestBody PurchaseRequest request) {
        return billingService.purchaseCredits(currentUser.require(), request.packId());
    }

    /**
     * Returnează istoricul de credite al utilizatorului curent.
     */
    @GetMapping("/credits/transactions")
    @Operation(summary = "Credit ledger of the current user")
    public PageResponse<TransactionResponse> transactions(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        AuthenticatedUser user = currentUser.require();
        return creditQueryService.history(user.id(), page, size, user);
    }

    /**
     * Returnează istoricul unui utilizator (proprietar sau operator).
     */
    @GetMapping("/users/{userId}/transactions")
    @Operation(summary = "Credit ledger of a user (self or operator)")
    public PageResponse<TransactionResponse> userTransactions(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return creditQueryService.history(userId, page, size, currentUser.require());
    }

    /**
     * Cerere de cumpărare a unui pachet de credite.
     */
    public record PurchaseRequest(@NotBlank String packId) {
    }
}
