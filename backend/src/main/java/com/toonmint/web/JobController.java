package com.toonmint.web;

import com.toonmint.domain.JobStatus;
import com.toonmint.security.CurrentUser;
import com.toonmint.service.JobService;
import com.toonmint.web.dto.JobResponse;
import com.toonmint.web.dto.JobStatsResponse;
import com.toonmint.web.dto.PageResponse;
import com.toonmint.web.dto.StatusUpdateRequest;
import com.toonmint.web.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@RestController
@RequestMapping("/api/jobs")
@Tag(name = "Jobs")
public class JobController {

    private final JobService jobService;
    private final CurrentUser currentUser;

    /**
     * Injectează serviciul de job-uri și contextul utilizatorului curent.
     */
    public JobController(JobService jobService, CurrentUser currentUser) {
        this.jobService = jobService;
        this.currentUser = currentUser;
    }

    /**
     * Creează un job din formular multipart (imagine + style_id).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Submit a photo for cartoon generation (consumes one credit)")
    public JobResponse create(@RequestParam("style_id") String styleId,
                              @RequestPart(value = "image", required = false) MultipartFile image,
                              @RequestParam(value = "image_base64", required = false) String imageBase64) {
        byte[] bytes = readImage(image, imageBase64);
        return jobService.createJob(currentUser.require().id(), bytes, styleId);
    }

    /**
     * Creează un job dintr-un payload JSON cu imaginea în base64.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Submit a photo as base64 JSON (consumes one credit)")
    public JobResponse createFromBase64(@Valid @RequestBody Base64JobRequest request) {
        return jobService.createJob(currentUser.require().id(),
                decode(request.imageBase64()), request.styleId());
    }

    /**
     * Returnează starea și metadatele unui job.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Job status and metadata")
    public JobResponse get(@PathVariable Long id) {
        return jobService.getJob(id, currentUser.require());
    }

    /**
     * Listează toate job-urile (doar operator).
     */
    @GetMapping
    @Operation(summary = "All jobs (operator only)")
    public PageResponse<JobResponse> list(@RequestParam(required = false) JobStatus status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        if (!currentUser.isAdmin()) {
            throw ApiException.forbidden("Only operators can list all jobs");
        }
        return jobService.listAllJobs(status, page, size);
    }

    /**
     * Schimbă statusul unui job (doar operator).
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "Change job status (operator only)")
    public JobResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return jobService.updateStatus(id, request.status(), currentUser.require());
    }

    /**
     * Șterge logic jobul și imaginea temporară.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a job (metadata and cached image)")
    public void delete(@PathVariable Long id) {
        jobService.deleteJob(id, currentUser.require());
    }

    /**
     * Reia un job eșuat.
     */
    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Retry a FAILED job (consumes one credit)")
    public JobResponse retry(@PathVariable Long id) {
        return jobService.retry(id, currentUser.require());
    }

    /**
     * Descarcă imaginea generată, dacă mai este în cache.
     */
    @GetMapping("/{id}/download")
    @Operation(summary = "Download the generated image while it is still cached")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        JobService.DownloadPayload payload = jobService.download(id, currentUser.require());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(payload.mimeType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(payload.filename(), StandardCharsets.UTF_8)
                .build());
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(payload.data());
    }

    /**
     * Caută job-uri după cuvânt cheie.
     */
    @GetMapping("/search")
    @Operation(summary = "Search jobs by style, status, error message or owner email")
    public PageResponse<JobResponse> search(@RequestParam String keyword,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return jobService.search(keyword, page, size, currentUser.require());
    }

    /**
     * Returnează statistici agregate pe interval (doar operator).
     */
    @GetMapping("/stats")
    @Operation(summary = "Aggregated statistics by style (operator only)")
    public JobStatsResponse stats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return jobService.stats(from, to, currentUser.require());
    }

    /**
     * Citește imaginea din multipart sau din câmpul base64.
     */
    private byte[] readImage(MultipartFile image, String imageBase64) {
        if (image != null && !image.isEmpty()) {
            try {
                return image.getBytes();
            } catch (IOException ex) {
                throw ApiException.badRequest("Could not read the uploaded image");
            }
        }
        if (imageBase64 != null && !imageBase64.isBlank()) {
            return decode(imageBase64);
        }
        throw ApiException.badRequest("Provide the photo as multipart 'image' or 'image_base64'");
    }

    /**
     * Decodează base64, acceptând și prefixul data-URI.
     */
    private byte[] decode(String base64) {
        String value = base64.trim();
        int comma = value.indexOf(',');
        if (value.startsWith("data:") && comma > 0) {
            value = value.substring(comma + 1);
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("image_base64 is not valid base64");
        }
    }

    /**
     * Payload JSON pentru upload-ul în base64.
     */
    public record Base64JobRequest(@jakarta.validation.constraints.NotBlank String styleId,
                                   @jakarta.validation.constraints.NotBlank String imageBase64) {
    }
}
