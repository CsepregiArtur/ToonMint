package com.toonmint.repository;

import com.toonmint.domain.CreditTransaction;
import com.toonmint.domain.CreditTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {

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
