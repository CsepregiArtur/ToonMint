package com.toonmint.ai;

public interface ImageGenerationProvider {

    /**
     * Generează o imagine pornind de la fotografia sursă și prompt.
     */
    GeneratedImage generate(byte[] source, String sourceMimeType, String prompt);

    /**
     * Numele providerului, folosit în loguri.
     */
    String name();
}
