package com.toonmint.repository;

import com.toonmint.domain.GuestTrial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuestTrialRepository extends JpaRepository<GuestTrial, Long> {

    /**
     * Verifică dacă adresa a consumat deja generarea gratuită.
     */
    boolean existsByEmail(String email);

    /**
     * Găsește revendicarea de trial pentru o adresă.
     */
    Optional<GuestTrial> findByEmail(String email);
}
