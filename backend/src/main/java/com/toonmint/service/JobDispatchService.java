package com.toonmint.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pornește procesarea numai după commit-ul tranzacției care a creat jobul, ca
 * worker-ul să nu vadă niciodată un job scris pe jumătate.
 */
@Service
public class JobDispatchService {

    private final JobProcessor jobProcessor;
    private final TaskExecutor jobExecutor;

    /**
     * Injectează procesorul și executorul de job-uri.
     */
    public JobDispatchService(JobProcessor jobProcessor,
                              @Qualifier("jobExecutor") TaskExecutor jobExecutor) {
        this.jobProcessor = jobProcessor;
        this.jobExecutor = jobExecutor;
    }

    /**
     * Listener declanșat după commit, care pornește procesarea.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobQueued(JobQueuedEvent event) {
        dispatch(event.jobId());
    }

    /**
     * Rulează procesarea pe alt thread: tranzacția care a creat job-ul s-a
     * încheiat, iar worker-ul deschide propriile tranzacții JPA curate.
     */
    public void dispatch(Long jobId) {
        jobExecutor.execute(() -> jobProcessor.processNow(jobId));
    }
}
