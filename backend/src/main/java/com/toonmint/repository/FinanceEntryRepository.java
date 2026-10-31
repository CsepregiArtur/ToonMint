package com.toonmint.repository;

import com.toonmint.domain.FinanceCategory;
import com.toonmint.domain.FinanceEntry;
import com.toonmint.domain.FinanceSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface FinanceEntryRepository extends JpaRepository<FinanceEntry, Long> {

    /**
     * Verifică dacă o intrare a fost deja scrisă pentru referința dată.
     */
    boolean existsBySourceAndReferenceTypeAndReferenceKeyAndCategory(
            FinanceSource source, String referenceType, String referenceKey, FinanceCategory category);

    /**
     * Listează intrările din interval, cele mai noi primele.
     */
    @Query("""
            select e from FinanceEntry e
            where e.occurredAt >= :from and e.occurredAt < :to
            order by e.occurredAt desc
            """)
    Page<FinanceEntry> findInRange(@Param("from") Instant from,
                                   @Param("to") Instant to,
                                   Pageable pageable);

    /**
     * Listează intrările unei categorii din interval, cele mai noi primele.
     */
    @Query("""
            select e from FinanceEntry e
            where e.category = :category
              and e.occurredAt >= :from and e.occurredAt < :to
            order by e.occurredAt desc
            """)
    Page<FinanceEntry> findByCategoryInRange(@Param("category") FinanceCategory category,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to,
                                            Pageable pageable);

    @Query("""
            select e.category as category, sum(e.amount * e.fxRate) as total
            from FinanceEntry e
            where e.occurredAt >= :from and e.occurredAt < :to
            group by e.category
            """)
    /**
     * Totaluri pe categorie în intervalul dat, în moneda de raportare.
     */
    List<CategoryTotal> totalsByCategory(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select e.category as category,
                   extract(year from e.occurredAt) as year,
                   extract(month from e.occurredAt) as month,
                   sum(e.amount * e.fxRate) as total
            from FinanceEntry e
            where e.occurredAt >= :from and e.occurredAt < :to
            group by e.category, extract(year from e.occurredAt), extract(month from e.occurredAt)
            """)
    /**
     * Totaluri pe lună și categorie, pentru seria temporală a raportului.
     */
    List<MonthlyTotal> totalsByMonth(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select e.productId as productId, sum(e.amount * e.fxRate) as total, count(e) as entries
            from FinanceEntry e
            where e.category = :category
              and e.productId is not null
              and e.occurredAt >= :from and e.occurredAt < :to
            group by e.productId
            order by sum(e.amount * e.fxRate) desc
            """)
    /**
     * Încasările grupate pe produs, în moneda de raportare.
     */
    List<ProductTotal> totalsByProduct(@Param("category") FinanceCategory category,
                                       @Param("from") Instant from,
                                       @Param("to") Instant to);

    @Query("""
            select count(e) from FinanceEntry e
            where e.category = :category
              and e.occurredAt >= :from and e.occurredAt < :to
            """)
    /**
     * Numără intrările unei categorii în interval (ex. generările facturate).
     */
    long countByCategory(@Param("category") FinanceCategory category,
                         @Param("from") Instant from,
                         @Param("to") Instant to);

    /**
     * Proiecție pentru totalurile pe categorie.
     */
    interface CategoryTotal {
        FinanceCategory getCategory();

        BigDecimal getTotal();
    }

    /**
     * Proiecție pentru totalurile lunare pe categorie.
     */
    interface MonthlyTotal {
        FinanceCategory getCategory();

        int getYear();

        int getMonth();

        BigDecimal getTotal();
    }

    /**
     * Proiecție pentru încasările pe produs.
     */
    interface ProductTotal {
        String getProductId();

        BigDecimal getTotal();

        long getEntries();
    }
}
