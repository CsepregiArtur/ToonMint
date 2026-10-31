package com.toonmint;

import com.toonmint.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StyleCatalogTest extends ApiTestSupport {

    private static final int MINIMUM_STYLES = 20;

    @Autowired
    private AppProperties properties;

    @Test
    void catalogOffersMoreThanTwentyDistinctStyles() {
        Map<String, AppProperties.Jobs.Style> styles = properties.jobs().styles();
        assertTrue(styles.size() >= MINIMUM_STYLES,
                "expected at least " + MINIMUM_STYLES + " styles, found " + styles.size());

        Set<String> labels = new HashSet<>();
        styles.forEach((id, style) -> {
            assertFalse(id.isBlank(), "style id must not be blank");
            assertFalse(style.label() == null || style.label().isBlank(),
                    "style " + id + " must have a label");
            assertTrue(style.prompt() != null && style.prompt().length() > 40,
                    "style " + id + " must have a meaningful prompt");
            assertTrue(labels.add(style.label().toLowerCase()),
                    "duplicate label for style " + id + ": " + style.label());
        });
        assertEquals(styles.size(), labels.size(), "labels must be unique");
    }

    @Test
    void publicEndpointExposesEveryStyle() throws Exception {
        mockMvc.perform(get("/api/styles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(MINIMUM_STYLES)))
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].label").isNotEmpty())
                .andExpect(jsonPath("$[0].previewUrl").isNotEmpty());
    }

    @Test
    void everyStyleHasAPreviewImageUrl() {
        String base = properties.jobs().stylePreviewBaseUrl();
        assertFalse(base == null || base.isBlank(), "style preview base url must be configured");
        properties.jobs().styles().keySet().forEach(id -> {
            // convenția folosită de frontend: <base>/<style-id>.jpg
            String expected = (base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                    + "/" + id + ".jpg";
            assertTrue(expected.endsWith("/" + id + ".jpg"));
        });
    }
}
