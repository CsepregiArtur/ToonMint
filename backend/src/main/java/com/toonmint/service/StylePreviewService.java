package com.toonmint.service;

import com.toonmint.ai.GeneratedImage;
import com.toonmint.ai.ImageGenerationProvider;
import com.toonmint.config.AppProperties;
import com.toonmint.web.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generează cu Gemini imaginile de exemplu pentru fiecare stil.
 *
 * Fiecare previzualizare este o generare reală a modelului, pornind de la fotografia
 * demo și de la promptul stilului, scrisă în folderul servit de frontend
 * ({@code app.jobs.style-preview-output-dir}). Nimic nu este simulat local.
 */
@Service
public class StylePreviewService {

    private static final Logger log = LoggerFactory.getLogger(StylePreviewService.class);

    private final ImageGenerationProvider provider;
    private final AppProperties properties;

    public StylePreviewService(ImageGenerationProvider provider, AppProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    /**
     * Raportează ce previzualizări există deja și ce stiluri mai lipsesc.
     */
    public PreviewStatus status() {
        Path dir = outputDir();
        List<String> missing = new ArrayList<>();
        List<String> present = new ArrayList<>();
        properties.jobs().styles().keySet().forEach(id -> {
            if (Files.isRegularFile(dir.resolve(id + ".jpg"))) {
                present.add(id);
            } else {
                missing.add(id);
            }
        });
        return new PreviewStatus(provider.name(), dir.toString(), sourceFile().toString(),
                present, missing, isGenerativeProvider());
    }

    /**
     * Generează previzualizările cerute (sau doar pe cele lipsă) apelând providerul AI.
     * Operația este secvențială și plătită per imagine — se rulează manual de operator.
     */
    public GenerationResult generate(List<String> requestedStyles, boolean missingOnly, int limit) {
        if (!isGenerativeProvider()) {
            throw new ApiException(HttpStatus.CONFLICT, "PREVIEW_REQUIRES_AI_PROVIDER",
                    "Previzualizările reale cer un provider AI. Setează AI_PROVIDER=gemini și GEMINI_API_KEY.");
        }
        Path source = sourceFile();
        byte[] photo;
        try {
            photo = Files.readAllBytes(source);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "PREVIEW_SOURCE_MISSING",
                    "Fotografia sursă pentru previzualizări nu există: " + source);
        }
        String mimeType = com.toonmint.ai.ImageBytes.detectMimeType(photo);
        if (mimeType == null) {
            throw new ApiException(HttpStatus.CONFLICT, "PREVIEW_SOURCE_MISSING",
                    "Fotografia sursă nu este o imagine suportată: " + source);
        }

        Path dir = outputDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "PREVIEW_DIR_UNWRITABLE",
                    "Nu pot scrie în folderul de previzualizări: " + dir);
        }

        List<String> targets = resolveTargets(requestedStyles, missingOnly, dir);
        if (limit > 0 && targets.size() > limit) {
            targets = targets.subList(0, limit);
        }

        List<String> generated = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        for (int index = 0; index < targets.size(); index++) {
            String styleId = targets.get(index);
            if (index > 0) {
                pauseBetweenGenerations();
            }
            AppProperties.Jobs.Style style = properties.jobs().styles().get(styleId);
            try {
                GeneratedImage image = provider.generate(photo, mimeType, style.prompt());
                if ("anime_v1".equals(styleId)) {
                    writeJpeg(image.data(), heroTarget(dir),
                            properties.jobs().stylePreviewHeroDimension());
                }
                writeJpeg(image.data(), dir.resolve(styleId + ".jpg"),
                        properties.jobs().stylePreviewMaxDimension());
                generated.add(styleId);
                log.info("Preview generated for style {} via {}", styleId, provider.name());
            } catch (RuntimeException ex) {
                log.warn("Preview generation failed for style {}: {}", styleId, ex.getMessage());
                failures.add(new Failure(styleId, ex.getMessage()));
            }
        }
        boolean hero = refreshHeroImage(dir, requestedStyles);
        return new GenerationResult(provider.name(), dir.toString(), generated, failures, hero);
    }

    /**
     * Așteaptă între două generări, ca cererile să nu depășească limita pe minut.
     */
    private void pauseBetweenGenerations() {
        long delay = properties.jobs().stylePreviewDelayMillis();
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Copiază exemplul stilului anime peste imaginea „după” din hero, ca demonstrația
     * de pe prima pagină să fie tot un rezultat real generat de model.
     */
    private boolean refreshHeroImage(Path dir, List<String> requested) {
        Path heroFile = heroTarget(dir);
        if (!Files.isRegularFile(heroFile)) {
            return false;
        }
        if (requested != null && !requested.isEmpty() && !requested.contains("anime_v1")) {
            return false;
        }
        log.info("Hero image ready: {}", heroFile);
        return true;
    }

    /**
     * Fișierul „după” folosit de slider-ul din prima pagină (frate al folderului de stiluri).
     */
    private Path heroTarget(Path dir) {
        Path parent = dir.getParent();
        return (parent == null ? dir : parent).resolve("after.jpg");
    }

    /**
     * Determină lista de stiluri de generat, ținând cont de cele deja existente.
     */
    private List<String> resolveTargets(List<String> requested, boolean missingOnly, Path dir) {
        List<String> ids = (requested == null || requested.isEmpty())
                ? new ArrayList<>(properties.jobs().styles().keySet())
                : new ArrayList<>(requested);
        ids.removeIf(id -> !properties.jobs().styles().containsKey(id));
        if (missingOnly) {
            ids.removeIf(id -> Files.isRegularFile(dir.resolve(id + ".jpg")));
        }
        ids.sort(Comparator.naturalOrder());
        return ids;
    }

    /**
     * Scrie imaginea generată ca JPEG, redimensionată la latura maximă cerută
     * (fișierele sunt servite paginii web, nu arhivate la rezoluție maximă).
     */
    private void writeJpeg(byte[] data, Path target, int maxDimension) {
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(data));
            if (decoded == null) {
                throw new IllegalStateException("providerul a returnat o imagine necitibilă");
            }
            ImageIO.write(downscale(decoded, maxDimension), "jpg", target.toFile());
        } catch (IOException ex) {
            throw new IllegalStateException("nu pot scrie fișierul " + target, ex);
        }
    }

    /**
     * Micșorează imaginea păstrând proporțiile, dacă depășește limita.
     */
    private BufferedImage downscale(BufferedImage image, int maxDimension) {
        if (maxDimension <= 0) {
            return image;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int longest = Math.max(width, height);
        if (longest <= maxDimension) {
            return image;
        }
        double scale = (double) maxDimension / longest;
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        var graphics = scaled.createGraphics();
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return scaled;
    }

    /**
     * Previzualizările sunt utile doar dacă providerul chiar generează imagini.
     */
    private boolean isGenerativeProvider() {
        return !provider.name().startsWith("stub");
    }

    private Path outputDir() {
        return Path.of(properties.jobs().stylePreviewOutputDir()).toAbsolutePath().normalize();
    }

    private Path sourceFile() {
        return Path.of(properties.jobs().stylePreviewSource()).toAbsolutePath().normalize();
    }

    /**
     * Starea previzualizărilor pentru consola de operator.
     */
    public record PreviewStatus(String provider,
                                String outputDir,
                                String source,
                                List<String> present,
                                List<String> missing,
                                boolean generativeProvider) {
    }

    /**
     * Rezultatul unei rulări de generare.
     */
    public record GenerationResult(String provider,
                                   String outputDir,
                                   List<String> generated,
                                   List<Failure> failures,
                                   boolean heroUpdated) {

        public Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("provider", provider);
            map.put("outputDir", outputDir);
            map.put("generated", generated);
            map.put("failed", failures);
            map.put("heroUpdated", heroUpdated);
            return map;
        }
    }

    /**
     * Un stil care nu a putut fi generat, împreună cu motivul.
     */
    public record Failure(String styleId, String reason) {
    }
}
