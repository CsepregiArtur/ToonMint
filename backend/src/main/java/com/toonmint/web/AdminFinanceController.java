package com.toonmint.web;

import com.toonmint.domain.FinanceCategory;
import com.toonmint.service.FinanceService;
import com.toonmint.web.dto.CreateFinanceEntryRequest;
import com.toonmint.web.dto.FinanceEntryResponse;
import com.toonmint.web.dto.FinanceSummaryResponse;
import com.toonmint.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Bookkeeping pentru operator: raportul de profitabilitate și registrul de
 * intrări financiare. Accesul este restricționat la rolul ADMIN.
 */
@RestController
@RequestMapping("/api/admin/finance")
@Tag(name = "Admin finance")
public class AdminFinanceController {

    private final FinanceService financeService;

    /**
     * Injectează serviciul financiar.
     */
    public AdminFinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    /**
     * Returnează încasările, costurile și marja pentru intervalul cerut.
     */
    @GetMapping("/summary")
    @Operation(summary = "Revenue, costs and margin for a period (operator only)")
    public FinanceSummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return financeService.summary(from, to);
    }

    /**
     * Listează intrările din registrul financiar.
     */
    @GetMapping("/entries")
    @Operation(summary = "Finance ledger entries (operator only)")
    public PageResponse<FinanceEntryResponse> entries(
            @RequestParam(required = false) FinanceCategory category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        return PageResponse.from(financeService.list(category, from, to, pageable), entry -> entry);
    }

    /**
     * Adaugă o cheltuială sau un venit introdus manual.
     */
    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a manual expense or income (operator only)")
    public FinanceEntryResponse addEntry(@Valid @RequestBody CreateFinanceEntryRequest request) {
        return financeService.addManual(request);
    }

    /**
     * Șterge o intrare introdusă manual.
     */
    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a manually added finance entry (operator only)")
    public void deleteEntry(@PathVariable Long id) {
        financeService.deleteManual(id);
    }
}
