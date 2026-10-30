package com.toonmint.repository;

import com.toonmint.domain.SubscriptionEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, Long> {

    /**
     * Verifică dacă evenimentul RevenueCat a mai fost primit.
     */
    boolean existsByEventId(String eventId);

    /**
     * Caută evenimentul după id-ul RevenueCat.
     */
    Optional<SubscriptionEvent> findByEventId(String eventId);

    /**
     * Ultimele evenimente primite, pentru consola de admin.
     */
    Page<SubscriptionEvent> findAllByOrderByReceivedAtDesc(Pageable pageable);
}
