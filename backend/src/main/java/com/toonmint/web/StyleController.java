package com.toonmint.web;

import com.toonmint.config.AppProperties;
import com.toonmint.web.dto.StyleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Publică lista de stiluri disponibile pentru selectorul din frontend: id, etichetă
 * și imaginea de exemplu. Prompturile rămân pe server.
 */
@RestController
@RequestMapping("/api/styles")
@Tag(name = "Styles")
public class StyleController {

    private final AppProperties properties;

    /**
     * Injectează configurația stilurilor.
     */
    public StyleController(AppProperties properties) {
        this.properties = properties;
    }

    /**
     * Returnează stilurile disponibile pentru selectorul din frontend.
     */
    @GetMapping
    @Operation(summary = "List available cartoon styles")
    public List<StyleResponse> styles() {
        String base = previewBase();
        return properties.jobs().styles().entrySet().stream()
                .map(entry -> new StyleResponse(
                        entry.getKey(),
                        entry.getValue().label(),
                        base == null ? null : base + "/" + entry.getKey() + ".jpg"))
                .toList();
    }

    /**
     * Prefixul public al imaginilor de exemplu; null dacă este dezactivat.
     */
    private String previewBase() {
        String base = properties.jobs().stylePreviewBaseUrl();
        if (base == null || base.isBlank()) {
            return null;
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
