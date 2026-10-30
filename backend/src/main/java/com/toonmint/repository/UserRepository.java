package com.toonmint.repository;

import com.toonmint.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Caută un utilizator după email, fără a ține cont de majuscule.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Caută un utilizator după identificatorul public (app_user_id).
     */
    Optional<User> findByPublicId(UUID publicId);

    /**
     * Verifică dacă există deja un cont cu acest email.
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Serializează modificările de credite pentru un utilizator. Orice schimbare de
     * sold trebuie să treacă prin acest lock, ca cererile concurente să nu poată
     * cheltui mai mult decât există în sold.
     */
    /**
     * Blochează rândul utilizatorului (SELECT ... FOR UPDATE) pentru operații pe credite.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
