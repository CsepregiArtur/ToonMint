package com.toonmint.ai;

import com.toonmint.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Provider offline pentru dezvoltare și teste: aplică un filtru posterize, ca
 * fluxul complet să poată fi testat fără o cheie Gemini.
 */
public class StubImageProvider implements ImageGenerationProvider {

    private static final Logger log = LoggerFactory.getLogger(StubImageProvider.class);
    private static final int LEVELS = 4;

    /**
     * Anunță în log că se folosește providerul local.
     */
    public StubImageProvider(AppProperties properties) {
        log.info("Using stub image provider (set AI_PROVIDER=gemini and GEMINI_API_KEY to call Gemini {})",
                properties.ai().model());
    }

    /**
     * Aplică un filtru posterize pentru a simula rezultatul AI local.
     */
    @Override
    public GeneratedImage generate(byte[] source, String sourceMimeType, String prompt) {
        BufferedImage input = decode(source);
        BufferedImage output = input == null
                ? placeholder()
                : posterize(input, Math.min(input.getWidth(), 1024));
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(output, "png", bytes);
            return new GeneratedImage(bytes.toByteArray(), "image/png");
        } catch (IOException ex) {
            throw new AiGenerationException("Stub provider could not encode the result", false, ex);
        }
    }

    /**
     * Numele providerului stub.
     */
    @Override
    public String name() {
        return "stub";
    }

    /**
     * Decodează imaginea sursă; întoarce null dacă nu poate fi citită.
     */
    private BufferedImage decode(byte[] source) {
        try {
            return ImageIO.read(new ByteArrayInputStream(source));
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * Reduce numărul de culori și redimensionează imaginea.
     */
    private BufferedImage posterize(BufferedImage input, int maxSize) {
        int width = input.getWidth();
        int height = input.getHeight();
        double scale = Math.min(1.0, (double) maxSize / Math.max(width, height));
        int targetWidth = Math.max(1, (int) (width * scale));
        int targetHeight = Math.max(1, (int) (height * scale));
        BufferedImage output = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        int step = 255 / (LEVELS - 1);
        for (int y = 0; y < targetHeight; y++) {
            int sourceY = Math.min(height - 1, (int) (y / scale));
            for (int x = 0; x < targetWidth; x++) {
                int sourceX = Math.min(width - 1, (int) (x / scale));
                int rgb = input.getRGB(sourceX, sourceY);
                int r = quantize((rgb >> 16) & 0xFF, step);
                int g = quantize((rgb >> 8) & 0xFF, step);
                int b = quantize(rgb & 0xFF, step);
                output.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return output;
    }

    /**
     * Aproximează o componentă de culoare la cel mai apropiat nivel.
     */
    private int quantize(int value, int step) {
        int quantized = Math.round((float) value / step) * step;
        return Math.min(255, Math.max(0, quantized));
    }

    /**
     * Creează o imagine de rezervă dacă sursa nu poate fi decodată.
     */
    private BufferedImage placeholder() {
        BufferedImage image = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setPaint(new java.awt.GradientPaint(0, 0, new Color(255, 170, 90), 512, 512, new Color(80, 60, 200)));
        graphics.fillRect(0, 0, 512, 512);
        graphics.dispose();
        return image;
    }
}
