package com.toonmint.repository;

import com.toonmint.domain.Job;
import com.toonmint.domain.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, Long> {

    /**
     * Toate joburile unui utilizator, pentru exportul de date (GDPR art. 20).
     */
    List<Job> findByUser_IdOrderByCreatedAtDesc(Long userId);

    /**
     * Șterge joburile unui utilizator la ștergerea contului (rămân doar metadate
     * fiscale în `credit_transactions`).
     */
    void deleteByUser_Id(Long userId);

    /**
     * Caută un job după identificatorul public.
     */
    Optional<Job> findByPublicId(UUID publicId);

    /**
     * Încarcă jobul împreună cu proprietarul, pentru worker.
     */
    @EntityGraph(attributePaths = "user")
    @Query("select j from Job j where j.id = :id")
    Optional<Job> findWithUserById(@Param("id") Long id);

    /**
     * Job-urile cu un status care au fost create înainte de un moment dat.
     * Folosit la recuperarea job-urilor rămase fără worker (vezi JobRecoveryService).
     */
    @EntityGraph(attributePaths = "user")
    List<Job> findByStatusAndCreatedAtBefore(JobStatus status, Instant createdAt);

    /**
     * Job-urile cu un status care au început procesarea înainte de un moment dat.
     */
    @EntityGraph(attributePaths = "user")
    List<Job> findByStatusAndStartedAtBefore(JobStatus status, Instant startedAt);

    /**
     * Custom query: job-urile unui utilizator filtrate după status, cele mai noi primele.
     */
    @EntityGraph(attributePaths = "user")
    Page<Job> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, JobStatus status, Pageable pageable);

    /**
     * Job-urile unui utilizator, cele mai noi primele.
     */
    @EntityGraph(attributePaths = "user")
    Page<Job> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Toate job-urile cu un anumit status, pentru operator.
     */
    @EntityGraph(attributePaths = "user")
    Page<Job> findByStatusOrderByCreatedAtDesc(JobStatus status, Pageable pageable);

    /**
     * Toate job-urile, cele mai noi primele.
     */
    @EntityGraph(attributePaths = "user")
    Page<Job> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    @Query("""
            select j from Job j left join j.user u
            where lower(j.styleId) like lower(concat('%', :keyword, '%'))
               or lower(j.status) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(j.errorMessage, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(j.guestEmail, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(u.email, '')) like lower(concat('%', :keyword, '%'))
            order by j.createdAt desc
            """)
    /**
     * Custom query: caută în stil, status, mesaj de eroare și emailul proprietarului.
     */
    Page<Job> search(@Param("keyword") String keyword, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    @Query("""
            select j from Job j join j.user u
            where u.id = :userId
              and (lower(j.styleId) like lower(concat('%', :keyword, '%'))
                   or lower(j.status) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(j.errorMessage, '')) like lower(concat('%', :keyword, '%')))
            order by j.createdAt desc
            """)
    /**
     * Custom query: caută doar în job-urile unui utilizator.
     */
    Page<Job> searchByUser(@Param("userId") Long userId, @Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select j.styleId as styleId,
                   count(j) as total,
                   sum(case when j.status = :completedStatus then 1 else 0 end) as completed,
                   sum(case when j.status = :failedStatus then 1 else 0 end) as failed,
                   coalesce(sum(j.creditsConsumed), 0) as credits
            from Job j
            where j.createdAt >= :from and j.createdAt < :to
            group by j.styleId
            order by j.styleId
            """)
    /**
     * Custom query cu GROUP BY: agregă job-urile pe stil în intervalul dat.
     */
    List<StyleStatsView> statsByStyle(@Param("from") Instant from,
                                      @Param("to") Instant to,
                                      @Param("completedStatus") JobStatus completedStatus,
                                      @Param("failedStatus") JobStatus failedStatus);

    @Query("""
            select count(j) from Job j
            where j.createdAt >= :from and j.createdAt < :to
            """)
    /**
     * Numără job-urile create în intervalul dat.
     */
    long countCreatedBetween(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Proiecție pentru statisticile agregate pe stil.
     */
    interface StyleStatsView {
        String getStyleId();

        long getTotal();

        long getCompleted();

        long getFailed();

        long getCredits();
    }
}
