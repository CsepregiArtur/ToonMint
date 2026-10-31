package com.toonmint.repository;

import com.toonmint.domain.VerificationCode;
import com.toonmint.domain.VerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

    /**
     * Șterge codurile unui utilizator (la ștergerea contului).
     */
    void deleteByUserId(Long userId);

    /**
     * Ultimul cod emis pentru un utilizator și un scop (folosit pentru cooldown).
     */
    Optional<VerificationCode> findTopByUserIdAndPurposeOrderByCreatedAtDesc(Long userId, VerificationPurpose purpose);

    /**
     * Ultimul cod încă nefolosit pentru un utilizator și un scop.
     */
    Optional<VerificationCode> findTopByUserIdAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            Long userId, VerificationPurpose purpose);

    /**
     * Invalidează toate codurile active ale unui utilizator pentru scopul dat.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update VerificationCode c
               set c.consumedAt = :now
             where c.userId = :userId
               and c.purpose = :purpose
               and c.consumedAt is null
            """)
    int consumeActive(@Param("userId") Long userId,
                      @Param("purpose") VerificationPurpose purpose,
                      @Param("now") Instant now);
}
