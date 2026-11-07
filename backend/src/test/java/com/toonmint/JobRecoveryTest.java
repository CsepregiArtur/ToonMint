package com.toonmint;

import com.toonmint.domain.CreditTransactionType;
import com.toonmint.domain.Job;
import com.toonmint.domain.JobStatus;
import com.toonmint.domain.User;
import com.toonmint.service.JobRecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recuperarea job-urilor rămase fără worker: fără ea, un job întrerupt de un
 * restart rămâne pentru totdeauna în PENDING/PROCESSING, iar clientul își
 * epuizează bucla de polling.
 */
class JobRecoveryTest extends ApiTestSupport {

    @Autowired
    private JobRecoveryService jobRecoveryService;

    @Test
    void interruptedJobWithoutCachedSourceIsFailedAndRefunded() throws Exception {
        String email = uniqueEmail("recover-missing");
        registerAndGetToken(email);
        giveCredits(email, 0);

        User user = userByEmail(email);
        Job job = jobRepository.save(new Job(user, "anime_v1", 1));

        // Nimic pus în TempImageStore: cache-ul în memorie a murit odată cu procesul.
        int recovered = jobRecoveryService.recoverJobsCreatedBefore(
                Instant.now().plusSeconds(5), Instant.now().plusSeconds(5));

        assertTrue(recovered >= 1);
        Job failed = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(JobStatus.FAILED, failed.getStatus());
        assertTrue(failed.getErrorMessage().contains("interrupted"));
        assertEquals(1, userByEmail(email).getCreditBalance(),
                "the consumed credit must be refunded exactly once");
        assertTrue(creditTransactionRepository.existsByReferenceJobIdAndType(
                job.getId(), CreditTransactionType.REFUND));
    }

    @Test
    void interruptedRunningJobIsRequeuedWhenSourceIsStillCached() throws Exception {
        String email = uniqueEmail("recover-requeue");
        registerAndGetToken(email);
        giveCredits(email, 0);

        User user = userByEmail(email);
        Job job = jobRepository.save(new Job(user, "anime_v1", 1));
        tempImageStore.putSource(job.getId(), pngPhoto(48, 48), "image/png");
        job.markProcessing();
        jobRepository.save(job);

        jobRecoveryService.recoverJobsCreatedBefore(
                Instant.now().plusSeconds(5), Instant.now().plusSeconds(5));

        Job recovered = waitForFinalState(job.getId());
        assertEquals(JobStatus.COMPLETED, recovered.getStatus(),
                "a job whose photo is still cached must be retried, not failed");
        assertEquals(0, userByEmail(email).getCreditBalance(),
                "re-queueing must not consume a second credit");
    }

    /**
     * Worker-ul rulează pe alt thread, deci așteptăm ca jobul să ajungă într-o stare finală.
     */
    private Job waitForFinalState(Long jobId) throws InterruptedException {
        for (int attempt = 0; attempt < 60; attempt++) {
            Job job = jobRepository.findById(jobId).orElseThrow();
            if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.FAILED) {
                return job;
            }
            Thread.sleep(250);
        }
        return jobRepository.findById(jobId).orElseThrow();
    }
}
