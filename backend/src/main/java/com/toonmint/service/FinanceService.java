package com.toonmint.service;

import com.toonmint.config.AppProperties;
import com.toonmint.domain.FinanceCategory;
import com.toonmint.domain.FinanceEntry;
import com.toonmint.domain.FinanceSource;
import com.toonmint.domain.Job;
import com.toonmint.repository.FinanceEntryRepository;
import com.toonmint.web.dto.CreateFinanceEntryRequest;
import com.toonmint.web.dto.FinanceEntryResponse;
import com.toonmint.web.dto.FinanceSummaryResponse;
import com.toonmint.web.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Registrul financiar al aplicației. Scrie automat costul fiecărei generări și
 * încasările din webhook-urile RevenueCat, primește cheltuieli manuale și
 * agregă totul într-un raport de profitabilitate.
 *
 * <p>Sumele sunt management accounting, nu contabilitate fiscală: documentele
 * fiscale sunt emise de magazine (merchant of record).</p>
 */
@Service
public class FinanceService {

    private static final Logger log = LoggerFactory.getLogger(FinanceService.class);

    private static final String REFERENCE_JOB = "JOB";
    private static final String REFERENCE_REVENUECAT_EVENT = "REVENUECAT_EVENT";
    private static final int MONEY_SCALE = 4;
    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T23:59:59Z");

    private final FinanceEntryRepository entries;
    private final AppProperties properties;

    /**
     * Injectează repository-ul de intrări și configurația aplicației.
     */
    public FinanceService(FinanceEntryRepository entries, AppProperties properties) {
        this.entries = entries;
        this.properties = properties;
    }

    /**
     * Înregistrează costul AI al unei generări finalizate (inclusiv trial-urile,
     * care sunt cost de achiziție). Idempotent pe id-ul jobului.
     */
    @Transactional
    public void recordGenerationCost(Job job) {
        if (job.getId() == null) {
            return;
        }
        BigDecimal cost = financeConfig().costPerGeneration();
        String label = job.isTrial() ? "Trial generation" : "Generation";
        recordOnce(FinanceSource.AUTO_JOB,
                FinanceCategory.AI_GENERATION,
                REFERENCE_JOB,
                job.getId().toString(),
                null,
                cost,
                financeConfig().reportingCurrency(),
                label + " (" + job.getStyleId() + ")",
                job.getCompletedAt());
    }

    /**
     * Înregistrează o încasare dintr-o plată confirmată de RevenueCat.
     */
    @Transactional
    public void recordSale(String eventId,
                           String productId,
                           BigDecimal amount,
                           String currency,
                           Instant occurredAt) {
        recordOnce(FinanceSource.AUTO_REVENUECAT, FinanceCategory.SALE,
                REFERENCE_REVENUECAT_EVENT, eventId, productId, amount, currency,
                "Sale (" + productId + ")", occurredAt);
    }

    /**
     * Înregistrează comisionul magazinului, când webhook-ul îl raportează.
     */
    @Transactional
    public void recordStoreFee(String eventId,
                               String productId,
                               BigDecimal amount,
                               String currency,
                               Instant occurredAt) {
        recordOnce(FinanceSource.AUTO_REVENUECAT, FinanceCategory.STORE_FEE,
                REFERENCE_REVENUECAT_EVENT, eventId, productId, amount, currency,
                "Store fee (" + productId + ")", occurredAt);
    }

    /**
     * Înregistrează o rambursare ca cheltuială, ca să scadă din încasări.
     */
    @Transactional
    public void recordRefund(String eventId,
                             String productId,
                             BigDecimal amount,
                             String currency,
                             Instant occurredAt) {
        recordOnce(FinanceSource.AUTO_REVENUECAT, FinanceCategory.REFUND,
                REFERENCE_REVENUECAT_EVENT, eventId, productId, amount, currency,
                "Refund (" + productId + ")", occurredAt);
    }

    /**
     * Adaugă o cheltuială sau un venit introdus de operator.
     */
    @Transactional
    public FinanceEntryResponse addManual(CreateFinanceEntryRequest request) {
        String currency = normalizeCurrency(request.currency());
        FinanceEntry entry = new FinanceEntry(
                request.category(),
                money(request.amount()),
                currency,
                fxRate(currency),
                FinanceSource.MANUAL,
                null,
                null,
                null,
                truncate(request.description()),
                request.occurredAt() == null ? Instant.now() : request.occurredAt());
        return FinanceEntryResponse.from(entries.save(entry), financeConfig().reportingCurrency());
    }

    /**
     * Șterge o intrare introdusă manual (corecții). Intrările automate rămân
     * imutabile, ca raportul să rămână reconciliabil cu joburile și webhook-urile.
     */
    @Transactional
    public void deleteManual(Long id) {
        FinanceEntry entry = entries.findById(id)
                .orElseThrow(() -> ApiException.notFound("Finance entry " + id + " does not exist"));
        if (entry.getSource() != FinanceSource.MANUAL) {
            throw ApiException.badRequest("Only manually added entries can be deleted");
        }
        entries.delete(entry);
    }

    /**
     * Listează intrările din interval, opțional filtrate după categorie.
     */
    @Transactional(readOnly = true)
    public Page<FinanceEntryResponse> list(FinanceCategory category,
                                          Instant from,
                                          Instant to,
                                          Pageable pageable) {
        validateRange(from, to, false);
        Instant effectiveFrom = from == null ? Instant.EPOCH : from;
        Instant effectiveTo = to == null ? FAR_FUTURE : to;
        String reportingCurrency = financeConfig().reportingCurrency();
        Page<FinanceEntry> page = category == null
                ? entries.findInRange(effectiveFrom, effectiveTo, pageable)
                : entries.findByCategoryInRange(category, effectiveFrom, effectiveTo, pageable);
        return page.map(entry -> FinanceEntryResponse.from(entry, reportingCurrency));
    }

    /**
     * Construiește raportul de profitabilitate pentru intervalul dat.
     */
    @Transactional(readOnly = true)
    public FinanceSummaryResponse summary(Instant from, Instant to) {
        validateRange(from, to, true);
        String currency = financeConfig().reportingCurrency();

        Map<FinanceCategory, BigDecimal> totals = new EnumMap<>(FinanceCategory.class);
        for (FinanceEntryRepository.CategoryTotal row : entries.totalsByCategory(from, to)) {
            totals.merge(row.getCategory(), nullSafe(row.getTotal()), BigDecimal::add);
        }

        BigDecimal grossRevenue = sum(totals, FinanceCategory.SALE, FinanceCategory.OTHER_INCOME);
        BigDecimal refunds = sum(totals, FinanceCategory.REFUND);
        BigDecimal storeFees = sum(totals, FinanceCategory.STORE_FEE);
        BigDecimal aiCost = sum(totals, FinanceCategory.AI_GENERATION);
        BigDecimal otherCost = sum(totals, FinanceCategory.HOSTING, FinanceCategory.DOMAIN,
                FinanceCategory.MARKETING, FinanceCategory.OTHER);

        BigDecimal netRevenue = grossRevenue.subtract(refunds).subtract(storeFees);
        BigDecimal grossMargin = netRevenue.subtract(aiCost);
        BigDecimal netProfit = grossMargin.subtract(otherCost);
        double marginRatio = netRevenue.signum() == 0
                ? 0
                : grossMargin.divide(netRevenue, 4, RoundingMode.HALF_UP).doubleValue();

        List<FinanceSummaryResponse.ProductRevenue> byProduct =
                entries.totalsByProduct(FinanceCategory.SALE, from, to).stream()
                        .map(row -> new FinanceSummaryResponse.ProductRevenue(
                                row.getProductId(), money(row.getTotal()), row.getEntries()))
                        .toList();

        return new FinanceSummaryResponse(
                from,
                to,
                currency,
                money(grossRevenue),
                money(refunds),
                money(storeFees),
                money(netRevenue),
                money(aiCost),
                money(otherCost),
                money(grossMargin),
                money(netProfit),
                marginRatio,
                entries.countByCategory(FinanceCategory.AI_GENERATION, from, to),
                money(financeConfig().costPerGeneration()),
                byProduct,
                monthly(entries.totalsByMonth(from, to)));
    }

    /**
     * Construiește seria lunară din totalurile pe categorie.
     */
    private List<FinanceSummaryResponse.MonthPoint> monthly(
            List<FinanceEntryRepository.MonthlyTotal> rows) {
        Map<String, MonthAccumulator> months = new TreeMap<>();
        for (FinanceEntryRepository.MonthlyTotal row : rows) {
            String key = String.format("%04d-%02d", row.getYear(), row.getMonth());
            months.computeIfAbsent(key, ignored -> new MonthAccumulator())
                    .add(row.getCategory(), row.getTotal());
        }
        return months.entrySet().stream()
                .map(entry -> entry.getValue().toPoint(entry.getKey()))
                .toList();
    }

    /**
     * Scrie o intrare o singură dată pentru (sursă, referință, categorie).
     */
    private void recordOnce(FinanceSource source,
                            FinanceCategory category,
                            String referenceType,
                            String referenceKey,
                            String productId,
                            BigDecimal amount,
                            String currency,
                            String description,
                            Instant occurredAt) {
        if (amount == null || amount.signum() <= 0) {
            log.warn("Skipping {} entry for {} {}: amount is missing or not positive", category, referenceType, referenceKey);
            return;
        }
        if (referenceKey != null && entries.existsBySourceAndReferenceTypeAndReferenceKeyAndCategory(
                source, referenceType, referenceKey, category)) {
            log.debug("Finance entry already exists for {} {} ({})", referenceType, referenceKey, category);
            return;
        }
        String normalizedCurrency = normalizeCurrency(currency);
        entries.save(new FinanceEntry(
                category,
                money(amount),
                normalizedCurrency,
                fxRate(normalizedCurrency),
                source,
                referenceType,
                referenceKey,
                productId,
                truncate(description),
                occurredAt == null ? Instant.now() : occurredAt));
    }

    /**
     * Verifică intervalul primit de la client.
     */
    private void validateRange(Instant from, Instant to, boolean required) {
        if (required && (from == null || to == null)) {
            throw ApiException.badRequest("Both 'from' and 'to' are required (ISO-8601)");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw ApiException.badRequest("'from' must be before 'to'");
        }
    }

    /**
     * Normalizează codul monedei, cu moneda de raportare ca implicit.
     */
    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return financeConfig().reportingCurrency();
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Cursul de schimb către moneda de raportare, înghețat la momentul înregistrării.
     */
    private BigDecimal fxRate(String currency) {
        AppProperties.Finance config = financeConfig();
        if (currency.equalsIgnoreCase(config.reportingCurrency())) {
            return BigDecimal.ONE;
        }
        Map<String, BigDecimal> rates = config.fxRates();
        BigDecimal rate = rates == null ? null : rates.get(currency);
        if (rate == null) {
            log.warn("No FX rate configured for {} → {}; using 1. Set app.finance.fx-rates.{}",
                    currency, config.reportingCurrency(), currency);
            return BigDecimal.ONE;
        }
        return rate;
    }

    /**
     * Citește configurația financiară, cu valori implicite dacă lipsește.
     */
    private AppProperties.Finance financeConfig() {
        AppProperties.Finance finance = properties.finance();
        return finance == null ? AppProperties.Finance.defaults() : finance;
    }

    /**
     * Sumează categoriile cerute din totalurile intervalului.
     */
    private static BigDecimal sum(Map<FinanceCategory, BigDecimal> totals, FinanceCategory... categories) {
        BigDecimal result = BigDecimal.ZERO;
        for (FinanceCategory category : categories) {
            result = result.add(totals.getOrDefault(category, BigDecimal.ZERO));
        }
        return result;
    }

    /**
     * Tratează valorile nule venite din agregările SQL.
     */
    private static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * Rotunjește o sumă la precizia folosită în raport.
     */
    private static BigDecimal money(BigDecimal value) {
        return nullSafe(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Limitează descrierea la dimensiunea coloanei.
     */
    private static String truncate(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    /**
     * Acumulează sumele unei luni pe categorii.
     */
    private static final class MonthAccumulator {

        private final Map<FinanceCategory, BigDecimal> totals = new EnumMap<>(FinanceCategory.class);

        void add(FinanceCategory category, BigDecimal amount) {
            totals.merge(category, nullSafe(amount), BigDecimal::add);
        }

        FinanceSummaryResponse.MonthPoint toPoint(String month) {
            BigDecimal grossRevenue = sum(totals, FinanceCategory.SALE, FinanceCategory.OTHER_INCOME);
            BigDecimal refunds = sum(totals, FinanceCategory.REFUND);
            BigDecimal storeFees = sum(totals, FinanceCategory.STORE_FEE);
            BigDecimal aiCost = sum(totals, FinanceCategory.AI_GENERATION);
            BigDecimal otherCost = sum(totals, FinanceCategory.HOSTING, FinanceCategory.DOMAIN,
                    FinanceCategory.MARKETING, FinanceCategory.OTHER);
            BigDecimal netRevenue = grossRevenue.subtract(refunds).subtract(storeFees);
            BigDecimal grossMargin = netRevenue.subtract(aiCost);
            return new FinanceSummaryResponse.MonthPoint(
                    month,
                    money(grossRevenue),
                    money(refunds),
                    money(storeFees),
                    money(netRevenue),
                    money(aiCost),
                    money(otherCost),
                    money(grossMargin),
                    money(grossMargin.subtract(otherCost)));
        }
    }
}
