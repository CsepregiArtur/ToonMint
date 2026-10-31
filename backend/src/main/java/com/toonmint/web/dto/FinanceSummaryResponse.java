package com.toonmint.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Raportul financiar al unui interval: bani încasați, costuri și marjă.
 * Toate sumele sunt în {@code currency} (moneda de raportare).
 */
public record FinanceSummaryResponse(
        Instant from,
        Instant to,
        String currency,
        BigDecimal grossRevenue,
        BigDecimal refunds,
        BigDecimal storeFees,
        BigDecimal netRevenue,
        BigDecimal aiCost,
        BigDecimal otherCost,
        BigDecimal grossMargin,
        BigDecimal netProfit,
        double marginRatio,
        long generations,
        BigDecimal costPerGeneration,
        List<ProductRevenue> revenueByProduct,
        List<MonthPoint> monthly) {

    /**
     * Încasările unui produs RevenueCat.
     */
    public record ProductRevenue(String productId, BigDecimal amount, long sales) {
    }

    /**
     * O lună din seria temporală a raportului ({@code month} = `YYYY-MM`).
     */
    public record MonthPoint(String month,
                             BigDecimal grossRevenue,
                             BigDecimal refunds,
                             BigDecimal storeFees,
                             BigDecimal netRevenue,
                             BigDecimal aiCost,
                             BigDecimal otherCost,
                             BigDecimal grossMargin,
                             BigDecimal netProfit) {
    }
}
