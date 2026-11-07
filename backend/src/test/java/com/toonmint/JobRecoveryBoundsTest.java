package com.toonmint;

import com.toonmint.config.AppProperties;
import com.toonmint.domain.JobStatus;
import com.toonmint.repository.JobRepository;
import com.toonmint.service.CreditService;
import com.toonmint.service.JobRecoveryService;
import com.toonmint.storage.TempImageStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Limitele de timp trimise către repository trebuie să poată fi legate ca
 * {@code java.sql.Timestamp}. {@link Instant#MAX} nu poate: conversia aruncă
 * {@code ArithmeticException: long overflow}, iar pe PostgreSQL asta oprește
 * aplicația la pornire (recuperarea job-urilor rulează pe ApplicationReadyEvent).
 *
 * <p>Testul rulează pe H2, care nu reproduce eroarea, deci verifică exact
 * proprietatea care contează: valoarea capturată e convertibilă.
 */
class JobRecoveryBoundsTest {

    private final JobRepository jobRepository = mock(JobRepository.class);

    private JobRecoveryService service() {
        when(jobRepository.findByStatusAndCreatedAtBefore(any(), any())).thenReturn(List.of());
        when(jobRepository.findByStatusAndStartedAtBefore(any(), any())).thenReturn(List.of());
        return new JobRecoveryService(
                jobRepository,
                mock(TempImageStore.class),
                mock(CreditService.class),
                mock(ApplicationEventPublisher.class),
                properties(),
                mock(PlatformTransactionManager.class));
    }

    @Test
    void startupRecoveryBindsInstantsThatJdbcCanRepresent() {
        service().recoverAfterStartup();

        Instant pending = capturePendingBound();
        Instant processing = captureProcessingBound();

        assertDoesNotThrow(() -> Timestamp.from(pending));
        assertDoesNotThrow(() -> Timestamp.from(processing));
        assertFalse(pending.isAfter(Instant.now()), "limita PENDING e momentul pornirii");
        assertFalse(processing.isAfter(Instant.now()), "limita PROCESSING e momentul pornirii");
    }

    @Test
    void unbindableBoundIsClampedInsteadOfCrashing() {
        service().recoverJobsCreatedBefore(Instant.MAX, Instant.MAX);

        assertDoesNotThrow(() -> Timestamp.from(capturePendingBound()));
        assertDoesNotThrow(() -> Timestamp.from(captureProcessingBound()));
    }

    private Instant capturePendingBound() {
        ArgumentCaptor<Instant> bound = ArgumentCaptor.forClass(Instant.class);
        verify(jobRepository).findByStatusAndCreatedAtBefore(eq(JobStatus.PENDING), bound.capture());
        return bound.getValue();
    }

    private Instant captureProcessingBound() {
        ArgumentCaptor<Instant> bound = ArgumentCaptor.forClass(Instant.class);
        verify(jobRepository).findByStatusAndStartedAtBefore(eq(JobStatus.PROCESSING), bound.capture());
        return bound.getValue();
    }

    private static AppProperties properties() {
        AppProperties.Jobs jobs = new AppProperties.Jobs(
                1, 1, 1024, 512, true, Duration.ofMinutes(10),
                null, null, null, 0, 512, 1024, null);
        return new AppProperties(
                null, null, null, jobs, null, null, null, null, null, null, null, null);
    }
}
