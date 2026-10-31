package com.toonmint.service;

import com.toonmint.domain.Job;
import com.toonmint.domain.JobStatus;
import com.toonmint.ai.GeneratedImage;
import com.toonmint.ai.ImageGenerationProvider;
import com.toonmint.ai.ImageOptimizer;
import com.toonmint.config.AppProperties;
import com.toonmint.repository.JobRepository;
import com.toonmint.storage.TempImageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class JobProcessor {

    private static final Logger log = LoggerFactory.getLogger(JobProcessor.class);

    private final JobRepository jobRepository;
    private final TempImageStore tempImageStore;
    private final ImageGenerationProvider imageProvider;
    private final ImageOptimizer imageOptimizer;
    private final CreditService creditService;
    private final FinanceService financeService;
    private final AppProperties properties;

    /**
     * Injectează dependențele necesare procesării unui job.
     */
    public JobProcessor(JobRepository jobRepository,
                        TempImageStore tempImageStore,
                        ImageGenerationProvider imageProvider,
                        ImageOptimizer imageOptimizer,
                        CreditService creditService,
                        FinanceService financeService,
                        AppProperties properties) {
        this.jobRepository = jobRepository;
        this.tempImageStore = tempImageStore;
        this.imageProvider = imageProvider;
        this.imageOptimizer = imageOptimizer;
        this.creditService = creditService;
        this.financeService = financeService;
        this.properties = properties;
    }

    /**
     * Procesează sincron jobul (apelat de pe thread-ul worker).
     */
    public void processNow(Long jobId) {
        process(jobId);
    }

    /**
     * Schimbă statusul, apelează providerul AI și salvează rezultatul în cache.
     */
    private void process(Long jobId) {
        Job job = jobRepository.findWithUserById(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.PENDING) {
            return;
        }
        job.markProcessing();
        jobRepository.save(job);

        Optional<TempImageStore.StoredImage> source = tempImageStore.getSource(jobId);
        if (source.isEmpty()) {
            fail(job, "Source image is no longer available (temporary cache expired). Please submit the photo again.");
            return;
        }

        try {
            String prompt = resolvePrompt(job.getStyleId());
            GeneratedImage generated = imageProvider.generate(
                    source.get().data(), source.get().mimeType(), prompt);
            GeneratedImage optimized = imageOptimizer.downscaleIfNeeded(
                    generated, properties.jobs().maxOutputDimension());

            tempImageStore.putResult(jobId, optimized.data(), optimized.mimeType());
            job.markCompleted();
            jobRepository.save(job);
            recordGenerationCost(job);
            log.info("Job {} completed with provider {}", jobId, imageProvider.name());
        } catch (RuntimeException ex) {
            log.warn("Job {} failed: {}", jobId, ex.getMessage());
            fail(job, ex.getMessage() == null ? "Image generation failed" : ex.getMessage());
        }
    }

    /**
     * Înregistrează costul AI al generării. Bookkeeping-ul nu poate bloca livrarea
     * imaginii, deci o eroare aici este doar logată.
     */
    private void recordGenerationCost(Job job) {
        try {
            financeService.recordGenerationCost(job);
        } catch (RuntimeException ex) {
            log.error("Could not record the AI cost for job {}", job.getId(), ex);
        }
    }

    /**
     * Marchează jobul eșuat și rambursează creditul.
     */
    private void fail(Job job, String reason) {
        job.markFailed(reason);
        jobRepository.save(job);
        boolean refunded = creditService.refund(job, reason);
        log.info("Job {} marked FAILED (refunded={})", job.getId(), refunded);
    }

    /**
     * Ia promptul stilului din configurație.
     */
    private String resolvePrompt(String styleId) {
        AppProperties.Jobs.Style style = properties.jobs().styles().get(styleId);
        return style == null ? "Turn this photo into a cartoon." : style.prompt();
    }
}
