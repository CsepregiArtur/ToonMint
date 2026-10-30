package com.toonmint.ai;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class ImageOptimizer {

    private static final Logger log = LoggerFactory.getLogger(ImageOptimizer.class);

    /**
     * Redimensionează rezultatul dacă depășește dimensiunea maximă configurată.
     */
    public GeneratedImage downscaleIfNeeded(GeneratedImage image, int maxDimension) {
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image.data()));
            if (decoded == null) {
                return image;
            }
            int width = decoded.getWidth();
            int height = decoded.getHeight();
            if (width <= maxDimension && height <= maxDimension) {
                return image;
            }
            BufferedImage scaled = Thumbnails.of(decoded)
                    .size(maxDimension, maxDimension)
                    .asBufferedImage();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", output);
            return new GeneratedImage(output.toByteArray(), "image/png");
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("Could not downscale generated image, returning original: {}", ex.getMessage());
            return image;
        }
    }
}
