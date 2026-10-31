package com.toonmint.repository;

import com.toonmint.domain.PurchaseAcknowledgement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PurchaseAcknowledgementRepository extends JpaRepository<PurchaseAcknowledgement, Long> {

    /**
     * Verifică dacă există o confirmare recentă pentru un produs (folosită ca poartă
     * înainte de a genera linkul de checkout).
     */
    boolean existsByUserIdAndProductIdAndAcceptedAtAfter(Long userId, String productId, Instant after);

    /**
     * Confirmările unui utilizator, cele mai noi primele (pentru exportul de date).
     */
    List<PurchaseAcknowledgement> findByUserIdOrderByAcceptedAtDesc(Long userId);
}
