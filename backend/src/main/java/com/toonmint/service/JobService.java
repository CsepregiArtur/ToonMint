package com.toonmint.service;

import com.toonmint.ai.ImageBytes;
import com.toonmint.config.AppProperties;
import com.toonmint.domain.Job;
import com.toonmint.domain.JobStatus;
import com.toonmint.domain.User;
import com.toonmint.repository.JobRepository;
import com.toonmint.ratelimit.RateLimitService;
import com.toonmint.security.AuthenticatedUser;
import com.toonmint.storage.TempImageStore;
import com.toonmint.web.dto.JobResponse;
import com.toonmint.web.dto.JobStatsResponse;
import com.toonmint.web.dto.PageResponse;
import com.toonmint.web.error.ApiException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final CreditService creditService;
    private final RateLimitService rateLimitService;
    private final TempImageStore tempImageStore;
    private final JobMapper jobMapper;
    private final AppProperties properties;
    private final ApplicationEventPublisher events;

    /**
     * Injectează repository-ul, serviciile de credite/rate limit și store-ul temporar.
     */
    public JobService(JobRepository jobRepository,
                      CreditService creditService,
                      RateLimitService rateLimitService,
                      TempImageStore tempImageStore,
                      JobMapper jobMapper,
                      AppProperties properties,
                      ApplicationEventPublisher events) {
        this.jobRepository = jobRepository;
        this.creditService = creditService;
        this.rateLimitService = rateLimitService;
        this.tempImageStore = tempImageStore;
        this.jobMapper = jobMapper;
        this.properties = properties;
        this.events = events;
    }

    /**
     * Validează imaginea, consumă un credit și creează jobul care va fi procesat asincron.
     */
    @Transactional
    public JobResponse createJob(Long userId, byte[] image, String styleId) {
        rateLimitService.check(userId);
        String mimeType = validateImage(image, styleId);

        User user = creditService.lockForUpdate(userId);
        int cost = properties.jobs().creditsPerJob();
        creditService.ensureSufficientCredits(user, cost);

        Job job = new Job(user, styleId, cost);
        job.setInputMimeType(mimeType);
        job.setInputSizeBytes((long) image.length);
        job = jobRepository.save(job);

        creditService.recordConsumption(user, job, cost);
        tempImageStore.putSource(job.getId(), image, mimeType);
        events.publishEvent(new JobQueuedEvent(job.getId()));
        return jobMapper.toResponse(job);
    }

    /**
     * Validează stilul și imaginea, apoi întoarce tipul MIME detectat.
     * Folosit atât de fluxul autentificat, cât și de cel de trial.
     */
    String validateImage(byte[] image, String styleId) {
        if (styleId == null || !properties.jobs().styles().containsKey(styleId)) {
            throw ApiException.badRequest("Unknown style_id. Supported styles: "
                    + String.join(", ", properties.jobs().styles().keySet()));
        }
        if (image == null || image.length == 0) {
            throw ApiException.badRequest("An image is required");
        }
        if (image.length > properties.jobs().maxInputBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE",
                    "Image exceeds the maximum size of " + properties.jobs().maxInputBytes() + " bytes");
        }
        String mimeType = ImageBytes.detectMimeType(image);
        if (mimeType == null) {
            throw ApiException.badRequest("Unsupported image format. Use PNG, JPEG or WEBP");
        }
        return mimeType;
    }

    /**
     * Încarcă un job de trial după identificatorul public (acces fără cont).
     */
    @Transactional(readOnly = true)
    public JobResponse getGuestJob(UUID publicId) {
        Job job = requireGuestJob(publicId);
        return jobMapper.toResponse(job);
    }

    /**
     * Descarcă imaginea unui job de trial folosind identificatorul public ca token.
     */
    @Transactional(readOnly = true)
    public DownloadPayload downloadGuest(UUID publicId) {
        Job job = requireGuestJob(publicId);
        return downloadPayload(job);
    }

    /**
     * Găsește jobul de trial după identificatorul public și verifică starea finalizării.
     */
    private Job requireGuestJob(UUID publicId) {
        return jobRepository.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.notFound("Job " + publicId + " not found"));
    }

    /**
     * Returnează un job dacă solicitantul este proprietarul sau operator.
     */
    @Transactional(readOnly = true)
    public JobResponse getJob(Long jobId, AuthenticatedUser currentUser) {
        Job job = requireVisibleJob(jobId, currentUser);
        return jobMapper.toResponse(job);
    }

    /**
     * Listează job-urile unui utilizator, opțional filtrate după status.
     */
    @Transactional(readOnly = true)
    public PageResponse<JobResponse> listUserJobs(Long userId,
                                                  JobStatus status,
                                                  int page,
                                                  int size,
                                                  AuthenticatedUser currentUser) {
        assertSelfOrAdmin(userId, currentUser);
        Pageable pageable = pageRequest(page, size);
        Page<Job> jobs = status == null
                ? jobRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : jobRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);
        return PageResponse.from(jobs, jobMapper::toResponse);
    }

    /**
     * Listează toate job-urile pentru operator.
     */
    @Transactional(readOnly = true)
    public PageResponse<JobResponse> listAllJobs(JobStatus status, int page, int size) {
        Pageable pageable = pageRequest(page, size);
        Page<Job> jobs = status == null
                ? jobRepository.findAllByOrderByCreatedAtDesc(pageable)
                : jobRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PageResponse.from(jobs, jobMapper::toResponse);
    }

    /**
     * Caută job-uri după cuvânt cheie (operator: tot; utilizator: doar ale sale).
     */
    @Transactional(readOnly = true)
    public PageResponse<JobResponse> search(String keyword, int page, int size, AuthenticatedUser currentUser) {
        if (keyword == null || keyword.isBlank()) {
            throw ApiException.badRequest("keyword is required");
        }
        String normalized = keyword.trim();
        Pageable pageable = pageRequest(page, size);
        Page<Job> jobs = currentUser.isAdmin()
                ? jobRepository.search(normalized, pageable)
                : jobRepository.searchByUser(currentUser.id(), normalized, pageable);
        return PageResponse.from(jobs, jobMapper::toResponse);
    }

    /**
     * Permite operatorului să marcheze jobul COMPLETED, FAILED (cu ramburs) sau REMOVED.
     */
    @Transactional
    public JobResponse updateStatus(Long jobId, JobStatus status, AuthenticatedUser currentUser) {
        if (!currentUser.isAdmin()) {
            throw ApiException.forbidden("Only operators can change job status");
        }
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> ApiException.notFound("Job " + jobId + " not found"));
        switch (status) {
            case COMPLETED -> job.markCompleted();
            case FAILED -> {
                job.markFailed("Marked as failed by an operator");
                creditService.refund(job, "Marked as failed by an operator");
            }
            case REMOVED -> {
                job.markRemoved();
                tempImageStore.evict(jobId);
            }
            default -> throw ApiException.badRequest(
                    "Operators can only set COMPLETED, FAILED or REMOVED");
        }
        return jobMapper.toResponse(job);
    }

    /**
     * Șterge logic jobul și elimină imaginea temporară.
     */
    @Transactional
    public void deleteJob(Long jobId, AuthenticatedUser currentUser) {
        Job job = requireVisibleJob(jobId, currentUser);
        job.markRemoved();
        tempImageStore.evict(jobId);
    }

    /**
     * Reia un job eșuat, consumând un credit nou, dacă sursa mai există în cache.
     */
    @Transactional
    public JobResponse retry(Long jobId, AuthenticatedUser currentUser) {
        rateLimitService.check(currentUser.id());
        Job job = requireVisibleJob(jobId, currentUser);
        if (job.getStatus() != JobStatus.FAILED) {
            throw ApiException.conflict("JOB_NOT_RETRYABLE", "Only FAILED jobs can be retried");
        }
        if (tempImageStore.getSource(jobId).isEmpty()) {
            throw new ApiException(HttpStatus.GONE, "SOURCE_IMAGE_EXPIRED",
                    "The original photo is no longer cached. Please submit the photo again.");
        }
        User user = creditService.lockForUpdate(job.getUser().getId());
        int cost = properties.jobs().creditsPerJob();
        creditService.ensureSufficientCredits(user, cost);

        job.resetForRetry();
        job.setCreditsConsumed(cost);
        creditService.recordConsumption(user, job, cost);
        events.publishEvent(new JobQueuedEvent(jobId));
        return jobMapper.toResponse(job);
    }

    /**
     * Returnează imaginea generată cât timp mai există în cache.
     */
    @Transactional(readOnly = true)
    public DownloadPayload download(Long jobId, AuthenticatedUser currentUser) {
        return downloadPayload(requireVisibleJob(jobId, currentUser));
    }

    /**
     * Construiește răspunsul de descărcare pentru un job finalizat aflat încă în cache.
     */
    private DownloadPayload downloadPayload(Job job) {
        if (job.getStatus() != JobStatus.COMPLETED) {
            throw ApiException.conflict("JOB_NOT_COMPLETED",
                    "The image is only available for COMPLETED jobs");
        }
        TempImageStore.StoredImage image = tempImageStore.getResult(job.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.GONE, "IMAGE_EXPIRED",
                        "The generated image is no longer cached (TTL expired). Generate it again if needed."));
        String extension = image.mimeType().contains("jpeg") || image.mimeType().contains("jpg")
                ? "jpg" : "png";
        return new DownloadPayload(image.data(), image.mimeType(),
                "toonmint-" + job.getPublicId() + "." + extension);
    }

    /**
     * Agregă statisticile pe stiluri în intervalul cerut (doar operator).
     */
    @Transactional(readOnly = true)
    public JobStatsResponse stats(Instant from, Instant to, AuthenticatedUser currentUser) {
        if (!currentUser.isAdmin()) {
            throw ApiException.forbidden("Only operators can read global statistics");
        }
        if (from == null || to == null) {
            throw ApiException.badRequest("Both 'from' and 'to' are required (ISO-8601)");
        }
        if (!from.isBefore(to)) {
            throw ApiException.badRequest("'from' must be before 'to'");
        }
        List<JobRepository.StyleStatsView> rows = jobRepository.statsByStyle(
                from, to, JobStatus.COMPLETED, JobStatus.FAILED);
        List<JobStatsResponse.StyleStats> byStyle = rows.stream()
                .map(row -> new JobStatsResponse.StyleStats(
                        row.getStyleId(), row.getTotal(), row.getCompleted(), row.getFailed(), row.getCredits()))
                .toList();
        long total = jobRepository.countCreatedBetween(from, to);
        long completed = byStyle.stream().mapToLong(JobStatsResponse.StyleStats::completed).sum();
        long failed = byStyle.stream().mapToLong(JobStatsResponse.StyleStats::failed).sum();
        long credits = byStyle.stream().mapToLong(JobStatsResponse.StyleStats::credits).sum();
        double successRate = total == 0 ? 0 : Math.round((completed * 10000.0) / total) / 10000.0;
        return new JobStatsResponse(from, to, total, completed, failed, successRate, credits, byStyle);
    }

    /**
     * Conținutul binar pregătit pentru descărcare.
     */
    public record DownloadPayload(byte[] data, String mimeType, String filename) {
    }

    /**
     * Încarcă jobul și verifică dreptul de acces.
     */
    private Job requireVisibleJob(Long jobId, AuthenticatedUser currentUser) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> ApiException.notFound("Job " + jobId + " not found"));
        if (!job.getUser().getId().equals(currentUser.id()) && !currentUser.isAdmin()) {
            throw ApiException.forbidden("You can only access your own jobs");
        }
        return job;
    }

    /**
     * Permite accesul doar proprietarului sau operatorului.
     */
    private void assertSelfOrAdmin(Long userId, AuthenticatedUser currentUser) {
        if (!userId.equals(currentUser.id()) && !currentUser.isAdmin()) {
            throw ApiException.forbidden("You can only access your own jobs");
        }
    }

    /**
     * Construiește o paginare cu limite sigure.
     */
    private Pageable pageRequest(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
    }
}
