package com.toonmint.service;

import com.toonmint.config.AppProperties;
import com.toonmint.domain.Job;
import com.toonmint.domain.JobStatus;
import com.toonmint.repository.JobRepository;
import com.toonmint.storage.TempImageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Repară job-urile rămase fără worker.
 *
 * <p>Fără el, un job al cărui proces moare în timpul generării rămâne veșnic
 * PENDING/PROCESSING: clientul își epuizează bucla de polling, creditul rămâne
 * consumat, iar jobul nu mai ajunge niciodată într-o stare finală. Recuperarea
 * rulează o dată la pornire (nimic nu poate fi în lucru atunci) și apoi periodic,
 * ca să prindă și job-urile pierdute fără restart.
 */
@Service
public class JobRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(JobRecoveryService.class);

    /**
     * Cât timp are un job PENDING la dispoziție ca să fie preluat de un worker.
     * Marja trebuie să acopere o așteptare normală în coadă, ca recuperarea să
     * nu pornească în paralel un job care e încă în queue.
     */
    private static final Duration DISPATCH_GRACE = Duration.ofMinutes(2);

    /** Valoarea folosită dacă proprietatea lipsește din configurație. */
    private static final Duration DEFAULT_MAX_PROCESSING = Duration.ofMinutes(10);

    private final JobRepository jobRepository;
    private final TempImageStore tempImageStore;
    private final CreditService creditService;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;
    private final TransactionTemplate transactionTemplate;

    /** Momentul pornirii procesului: orice job mai vechi a fost abandonat. */
    private final Instant processStartedAt = Instant.now();

    /**
     * Injectează repository-ul, cache-ul temporar, serviciul de credite și publisher-ul.
     * Tranzacția este gestionată explicit, pentru că metodele declanșate de evenimente
     * sau de scheduler nu trec prin proxy-ul tranzacțional.
     */
    public JobRecoveryService(JobRepository jobRepository,
                              TempImageStore tempImageStore,
                              CreditService creditService,
                              ApplicationEventPublisher events,
                              AppProperties properties,
                              PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.tempImageStore = tempImageStore;
        this.creditService = creditService;
        this.events = events;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * La pornire nu există niciun worker activ, deci orice job rămas PENDING sau
     * PROCESSING aparține procesului anterior.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterStartup() {
        recoverJobsCreatedBefore(processStartedAt, Instant.MAX);
    }

    /**
     * Prinde job-urile pierdute fără restart: cele rămase prea mult în coadă și
     * cele care depășesc durata maximă de procesare.
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void sweepStuckJobs() {
        recoverJobsCreatedBefore(
                Instant.now().minus(DISPATCH_GRACE),
                Instant.now().minus(maxProcessing()));
    }

    /**
     * Reia sau închide job-urile cu un status neterminat mai vechi decât limitele date.
     *
     * <p>Un job poate fi reluat doar dacă poza sursă mai există în cache (în
     * producție cache-ul e Redis, deci supraviețuiește unui restart). Altfel jobul
     * este marcat FAILED, iar creditul este rambursat o singură dată.
     *
     * @return numărul de job-uri reparate
     */
    public int recoverJobsCreatedBefore(Instant pendingCreatedBefore, Instant processingStartedBefore) {
        if (!recoveryEnabled()) {
            return 0;
        }
        Integer recovered = transactionTemplate.execute(status -> {
            int count = requeueOrFail(jobRepository.findByStatusAndCreatedAtBefore(
                    JobStatus.PENDING, pendingCreatedBefore), "before it started");
            count += requeueOrFail(jobRepository.findByStatusAndStartedAtBefore(
                    JobStatus.PROCESSING, processingStartedBefore), "while it was running");
            return count;
        });
        if (recovered != null && recovered > 0) {
            log.warn("Job recovery repaired {} unfinished job(s)", recovered);
        }
        return recovered == null ? 0 : recovered;
    }

    /**
     * Reia joburile care încă au poza în cache și închide restul cu rambursare.
     */
    private int requeueOrFail(List<Job> jobs, String interruptedPhase) {
        int count = 0;
        for (Job job : jobs) {
            if (tempImageStore.getSource(job.getId()).isPresent()) {
                if (job.getStatus() == JobStatus.PROCESSING) {
                    job.resetForRetry();
                    jobRepository.save(job);
                }
                // Evenimentul pornește worker-ul abia după commit, ca worker-ul să
                // citească starea PENDING scrisă aici.
                events.publishEvent(new JobQueuedEvent(job.getId()));
                log.info("Re-queued job {} interrupted {}", job.getId(), interruptedPhase);
            } else {
                String reason = "Generation was interrupted " + interruptedPhase
                        + " and the photo is no longer cached. Your credit has been refunded,"
                        + " please submit the photo again.";
                job.markFailed(reason);
                jobRepository.save(job);
                boolean refunded = creditService.refund(job, reason);
                log.warn("Closed interrupted job {} as FAILED (refunded={})", job.getId(), refunded);
            }
            count++;
        }
        return count;
    }

    /**
     * Recuperarea se poate opri din configurație pentru implementările multi-inststanță.
     */
    private boolean recoveryEnabled() {
        return properties.jobs().recoveryEnabled();
    }

    /**
     * Durata maximă de procesare configurată, cu fallback dacă lipsește.
     */
    private Duration maxProcessing() {
        Duration configured = properties.jobs().maxProcessing();
        return configured == null || configured.isNegative() || configured.isZero()
                ? DEFAULT_MAX_PROCESSING : configured;
    }
}
