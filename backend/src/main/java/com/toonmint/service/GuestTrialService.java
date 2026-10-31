package com.toonmint.service;

import com.toonmint.domain.GuestTrial;
import com.toonmint.domain.Job;
import com.toonmint.ratelimit.RateLimitService;
import com.toonmint.repository.GuestTrialRepository;
import com.toonmint.repository.JobRepository;
import com.toonmint.storage.TempImageStore;
import com.toonmint.web.dto.JobResponse;
import com.toonmint.web.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Generarea gratuită, fără cont: o singură imagine per adresă de email.
 * Emailul este revendicat atomic (coloană unică), deci două cereri simultane
 * pentru aceeași adresă nu pot produce două imagini.
 */
@Service
public class GuestTrialService {

    private static final Logger log = LoggerFactory.getLogger(GuestTrialService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

    private final GuestTrialRepository trials;
    private final JobRepository jobRepository;
    private final JobService jobService;
    private final JobMapper jobMapper;
    private final TempImageStore tempImageStore;
    private final RateLimitService rateLimitService;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;

    public GuestTrialService(GuestTrialRepository trials,
                             JobRepository jobRepository,
                             JobService jobService,
                             JobMapper jobMapper,
                             TempImageStore tempImageStore,
                             RateLimitService rateLimitService,
                             ApplicationEventPublisher events,
                             PlatformTransactionManager transactionManager) {
        this.trials = trials;
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.jobMapper = jobMapper;
        this.tempImageStore = tempImageStore;
        this.rateLimitService = rateLimitService;
        this.events = events;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Creează jobul gratuit pentru adresa dată. A doua cerere cu același email
     * primește 402, iar clientul este îndrumat spre abonament sau credite.
     */
    public JobResponse createTrial(String rawEmail, byte[] image, String styleId, String clientIp) {
        String email = normalizeEmail(rawEmail);
        rateLimitService.check("trial-email:" + email);
        rateLimitService.check("trial-ip:" + (clientIp == null ? "unknown" : clientIp));
        String mimeType = jobService.validateImage(image, styleId);

        if (trials.existsByEmail(email)) {
            throw trialAlreadyUsed();
        }

        try {
            return transactions.execute(status -> {
                Job job = jobRepository.save(Job.forGuest(email, styleId));
                job.setInputMimeType(mimeType);
                job.setInputSizeBytes((long) image.length);
                trials.save(new GuestTrial(email, job.getId()));
                tempImageStore.putSource(job.getId(), image, mimeType);
                events.publishEvent(new JobQueuedEvent(job.getId()));
                log.info("Guest trial started for {} (job {})", email, job.getId());
                return jobMapper.toResponse(job);
            });
        } catch (DataIntegrityViolationException ex) {
            throw trialAlreadyUsed();
        }
    }

    /**
     * Verifică dacă adresa mai poate primi imaginea gratuită (folosit în UI înainte de upload).
     */
    public boolean isEligible(String rawEmail) {
        return !trials.existsByEmail(normalizeEmail(rawEmail));
    }

    /**
     * Normalizează și validează adresa de email.
     */
    public String normalizeEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            throw ApiException.badRequest("Adresa de email este obligatorie.");
        }
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 255 || !EMAIL.matcher(email).matches()) {
            throw ApiException.badRequest("Adresa de email nu este validă.");
        }
        return email;
    }

    private ApiException trialAlreadyUsed() {
        return new ApiException(HttpStatus.PAYMENT_REQUIRED, "TRIAL_ALREADY_USED",
                "Ai folosit deja imaginea gratuită pentru această adresă. "
                        + "Alege un abonament sau cumpără credite pentru a continua.",
                Map.of("subscribeEndpoint", "/billing/catalog",
                        "registerEndpoint", "/auth/register"));
    }
}
