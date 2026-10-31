package com.toonmint.web;

import com.toonmint.service.StylePreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Administrare a imaginilor de exemplu pentru stiluri. Generarea se face exclusiv cu
 * providerul AI configurat (Gemini), o singură dată, de către operator.
 */
@RestController
@RequestMapping("/api/admin/style-previews")
@Tag(name = "Admin")
public class StylePreviewController {

    private final StylePreviewService previews;

    public StylePreviewController(StylePreviewService previews) {
        this.previews = previews;
    }

    /**
     * Arată ce previzualizări există și ce stiluri mai trebuie generate.
     */
    @GetMapping
    @Operation(summary = "Style preview status (operator only)")
    public StylePreviewService.PreviewStatus status() {
        return previews.status();
    }

    /**
     * Generează previzualizările lipsă (sau pe cele cerute) cu providerul AI.
     * Operația este secvențială: fiecare imagine este o generare plătită.
     */
    @PostMapping("/regenerate")
    @Operation(summary = "Generate style previews with the configured AI provider (operator only)")
    public Map<String, Object> regenerate(
            @RequestParam(defaultValue = "true") boolean missingOnly,
            @RequestParam(defaultValue = "0") int limit,
            @RequestBody(required = false) RegenerateRequest request) {
        List<String> styles = request == null ? List.of() : request.styles();
        return previews.generate(styles, missingOnly, limit).asMap();
    }

    /**
     * Lista opțională de stiluri de regenerat.
     */
    public record RegenerateRequest(List<String> styles) {
    }
}
