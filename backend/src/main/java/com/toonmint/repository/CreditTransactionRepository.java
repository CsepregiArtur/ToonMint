package com.toonmint.repository;

import com.toonmint.domain.CreditTransaction;
import com.toonmint.domain.CreditTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {

    /**
     * Toate tranzacțiile unui utilizator, pentru exportul de date.
     */
    List<CreditTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Rupe legătura cu joburile înainte de a le șterge la cererea de ștergere a
     * contului. Tranzacțiile rămân (obligație contabilă), doar referința dispare.
     */
    @Modifying(flushAutomatically = true)
    @Query("update CreditTransaction t set t.referenceJob = null where t.user.id = :userId")
    int clearJobReferences(@Param("userId") Long userId);

    /**
     * Istoricul tranzacțiilor unui utilizator, cele mai noi primele.
     */
    Page<CreditTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Caută o tranzacție după referința externă.
     */
    Optional<CreditTransaction> findByExternalReference(String externalReference);

    /**
     * Verifică dacă o referință externă a fost deja procesată (idempotență).
     */
    boolean existsByExternalReference(String externalReference);

    /**
     * Verifică dacă jobul a primit deja un ramburs, pentru a evita dublarea.
     */
    boolean existsByReferenceJobIdAndType(Long referenceJobId, CreditTransactionType type);
}
