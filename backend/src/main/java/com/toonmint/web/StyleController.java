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
 * Publica lista de stiluri disponibile pentru selectorul din frontend.
 * Expune doar id-ul și eticheta; prompturile rămân pe server.
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
        return properties.jobs().styles().entrySet().stream()
                .map(entry -> new StyleResponse(entry.getKey(), entry.getValue().label()))
                .toList();
    }
}
