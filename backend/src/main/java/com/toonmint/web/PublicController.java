package com.toonmint.web;

import com.toonmint.service.GuestTrialService;
import com.toonmint.service.JobService;
import com.toonmint.web.dto.JobResponse;
import com.toonmint.web.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Generarea gratuită, fără cont. Identificatorul public al jobului (UUID)
 * funcționează ca token: cine îl are poate citi statusul și descărca imaginea.
 */
@RestController
@RequestMapping("/api/public")
@Tag(name = "Public trial")
public class PublicController {

    private final GuestTrialService guestTrialService;
    private final JobService jobService;

    public PublicController(GuestTrialService guestTrialService, JobService jobService) {
        this.guestTrialService = guestTrialService;
        this.jobService = jobService;
    }

    /**
     * Creează imaginea gratuită din formular multipart (email + stil + imagine).
     */
    @PostMapping(value = "/trial", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "One free generation per email, no account required")
    public JobResponse createTrial(@RequestParam("email") String email,
                                   @RequestParam("style_id") String styleId,
                                   @RequestPart(value = "image", required = false) MultipartFile image,
                                   @RequestParam(value = "image_base64", required = false) String imageBase64,
                                   HttpServletRequest request) {
        return guestTrialService.createTrial(email, readImage(image, imageBase64), styleId,
                clientIp(request));
    }

    /**
     * Creează imaginea gratuită dintr-un payload JSON cu imaginea în base64.
     */
    @PostMapping(value = "/trial", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Same as the multipart variant, with a base64 payload")
    public JobResponse createTrialJson(@org.springframework.web.bind.annotation.RequestBody
                                       Base64TrialRequest body,
                                       HttpServletRequest request) {
        return guestTrialService.createTrial(body.email(), decode(body.imageBase64()), body.styleId(),
                clientIp(request));
    }

    /**
     * Verifică dacă adresa mai are dreptul la imaginea gratuită.
     */
    @GetMapping("/trial/eligibility")
    @Operation(summary = "Check whether an email can still use the free generation")
    public Map<String, Object> eligibility(@RequestParam String email) {
        return Map.of("eligible", guestTrialService.isEligible(email));
    }

    /**
     * Returnează statusul jobului gratuit, folosind identificatorul public.
     */
    @GetMapping("/jobs/{publicId}")
    @Operation(summary = "Trial job status")
    public JobResponse status(@PathVariable UUID publicId) {
        return jobService.getGuestJob(publicId);
    }

    /**
     * Descarcă imaginea generată cât timp mai există în cache.
     */
    @GetMapping("/jobs/{publicId}/download")
    @Operation(summary = "Download the trial image while it is still cached")
    public ResponseEntity<byte[]> download(@PathVariable UUID publicId) {
        JobService.DownloadPayload payload = jobService.downloadGuest(publicId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(payload.mimeType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(payload.filename(), StandardCharsets.UTF_8)
                .build());
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(payload.data());
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private byte[] readImage(MultipartFile image, String imageBase64) {
        if (image != null && !image.isEmpty()) {
            try {
                return image.getBytes();
            } catch (IOException ex) {
                throw ApiException.badRequest("Imaginea nu a putut fi citită.");
            }
        }
        if (imageBase64 != null && !imageBase64.isBlank()) {
            return decode(imageBase64);
        }
        throw ApiException.badRequest("Trimite fotografia ca 'image' sau ca 'image_base64'.");
    }

    private byte[] decode(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw ApiException.badRequest("image_base64 lipsește.");
        }
        String value = base64.trim();
        int comma = value.indexOf(',');
        if (value.startsWith("data:") && comma > 0) {
            value = value.substring(comma + 1);
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("image_base64 nu este base64 valid.");
        }
    }

    /**
     * Payload JSON pentru trial: email, stil și imagine în base64.
     */
    public record Base64TrialRequest(String email, String styleId, String imageBase64) {
    }
}
