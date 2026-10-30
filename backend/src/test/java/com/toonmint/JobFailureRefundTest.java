package com.toonmint;

import com.toonmint.domain.CreditTransactionType;
import com.toonmint.domain.Job;
import com.toonmint.domain.JobStatus;
import com.toonmint.domain.User;
import com.toonmint.service.JobProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobFailureRefundTest extends ApiTestSupport {

    @Autowired
    private JobProcessor jobProcessor;

    @Test
    void failedJobRefundsCreditsExactlyOnce() throws Exception {
        String email = uniqueEmail("refund");
        registerAndGetToken(email);
        giveCredits(email, 0);

        User user = userByEmail(email);
        Job job = jobRepository.save(new Job(user, "anime_v1", 1));

        jobProcessor.processNow(job.getId());

        Job failed = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals(JobStatus.FAILED, failed.getStatus());
        assertTrue(failed.getErrorMessage().contains("no longer available"));
        assertEquals(1, userByEmail(email).getCreditBalance());
        assertTrue(creditTransactionRepository.existsByReferenceJobIdAndType(
                job.getId(), CreditTransactionType.REFUND));

        jobProcessor.processNow(job.getId());
        assertEquals(1, userByEmail(email).getCreditBalance(),
                "a second processing attempt must not refund again");
    }
}
