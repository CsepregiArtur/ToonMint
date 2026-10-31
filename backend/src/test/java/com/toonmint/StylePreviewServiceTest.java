package com.toonmint;

import com.toonmint.ai.GeneratedImage;
import com.toonmint.ai.ImageGenerationProvider;
import com.toonmint.config.AppProperties;
import com.toonmint.service.StylePreviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StylePreviewServiceTest {

    @TempDir
    Path tmp;

    @Test
    void writesOneJpegPerRequestedStyle() throws Exception {
        Path source = writeSource();
        Path outputDir = tmp.resolve("styles");
        StylePreviewService service = new StylePreviewService(
                new FakeProvider("gemini:test-model"), properties(source, outputDir, 2));

        StylePreviewService.GenerationResult result = service.generate(List.of("anime_v1"), false, 0);

        assertEquals(List.of("anime_v1"), result.generated());
        assertTrue(result.failures().isEmpty());
        Path written = outputDir.resolve("anime_v1.jpg");
        assertTrue(Files.isRegularFile(written), "preview file must exist");
        BufferedImage decoded = ImageIO.read(written.toFile());
        assertEquals(64, decoded.getWidth());
    }

    @Test
    void missingOnlySkipsStylesThatAlreadyHaveAPreview() throws Exception {
        Path source = writeSource();
        Path outputDir = tmp.resolve("styles");
        Files.createDirectories(outputDir);
        Files.copy(writeSource(), outputDir.resolve("anime_v1.jpg"));

        StylePreviewService service = new StylePreviewService(
                new FakeProvider("gemini:test-model"), properties(source, outputDir, 2));

        StylePreviewService.GenerationResult result = service.generate(List.of(), true, 0);

        assertEquals(List.of("cartoon_v2"), result.generated());
        assertEquals(2, service.status().present().size());
        assertTrue(service.status().missing().isEmpty());
    }

    @Test
    void stubProviderIsRefused() throws Exception {
        Path source = writeSource();
        StylePreviewService service = new StylePreviewService(
                new FakeProvider("stub"), properties(source, tmp.resolve("styles"), 2));

        assertFalse(service.status().generativeProvider());
        assertThrows(com.toonmint.web.error.ApiException.class,
                () -> service.generate(List.of(), false, 0));
    }

    @Test
    void missingSourcePhotoIsReported() throws Exception {
        StylePreviewService service = new StylePreviewService(
                new FakeProvider("gemini:test-model"),
                properties(tmp.resolve("nope.png"), tmp.resolve("styles"), 2));

        com.toonmint.web.error.ApiException ex = assertThrows(com.toonmint.web.error.ApiException.class,
                () -> service.generate(List.of(), false, 0));
        assertEquals("PREVIEW_SOURCE_MISSING", ex.getCode());
    }

    private Path writeSource() throws Exception {
        BufferedImage image = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(200, 120, 60));
        graphics.fillRect(0, 0, 64, 48);
        graphics.dispose();
        Path path = tmp.resolve("before.png");
        ImageIO.write(image, "png", path.toFile());
        return path;
    }

    private AppProperties properties(Path source, Path outputDir, int styleCount) {
        Map<String, AppProperties.Jobs.Style> styles = new LinkedHashMap<>();
        styles.put("anime_v1", new AppProperties.Jobs.Style("Anime", "Create an anime image."));
        if (styleCount > 1) {
            styles.put("cartoon_v2", new AppProperties.Jobs.Style("Cartoon", "Create a cartoon image."));
        }
        return new AppProperties(
                new AppProperties.Jwt("0123456789012345678901234567890123", Duration.ofHours(1)),
                new AppProperties.Legal("2026-10-31"),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Jobs(1, 1, 10_485_760, 1024,
                        "/demo/styles", outputDir.toString(), source.toString(), 0L, 64, 64, styles),
                new AppProperties.RateLimit(false, "memory", 10),
                new AppProperties.TempStore("memory", Duration.ofMinutes(10), 100),
                new AppProperties.Ai("stub", "", "model", "https://example.test", 1,
                        Duration.ofMillis(1), Duration.ofMillis(1), Duration.ofSeconds(1)),
                new AppProperties.Mail(false, "no-reply@test", "Test"),
                new AppProperties.RevenueCat("", "", "pro", List.of()),
                new AppProperties.Admin("", ""),
                new AppProperties.Demo(false, "d@test", "p", 0, "a@test", "p", 0),
                AppProperties.Finance.defaults());
    }

    /**
     * Provider fals: întoarce o imagine PNG generată local, ca să validăm doar scrierea
     * fișierelor (fără apel real către Gemini).
     */
    private static final class FakeProvider implements ImageGenerationProvider {

        private final String name;

        private FakeProvider(String name) {
            this.name = name;
        }

        @Override
        public GeneratedImage generate(byte[] source, String sourceMimeType, String prompt) {
            try {
                BufferedImage image = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                graphics.setColor(new Color(20, 40, 220));
                graphics.fillRect(0, 0, 64, 48);
                graphics.dispose();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                return new GeneratedImage(out.toByteArray(), "image/png");
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public String name() {
            return name;
        }
    }
}
