package com.toonmint.repository;

import com.toonmint.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Găsește tokenul după hash-ul lui (singura formă stocată).
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Anulează toate tokenurile active ale unui utilizator (logout global,
     * refolosirea unui token revocat sau schimbarea parolei).
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshToken t
               set t.revokedAt = :now
             where t.userId = :userId
               and t.revokedAt is null
            """)
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    /**
     * Șterge tokenurile unui utilizator (la ștergerea contului).
     */
    void deleteByUserId(Long userId);

    /**
     * Șterge tokenurile expirate, ca tabela să nu crească la nesfârșit.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from RefreshToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
