package com.toonmint.storage;

import java.util.Optional;

/**
 * Cache temporar (memorie sau Redis) pentru imaginile încărcate și cele generate.
 * Nimic nu este persistat: intrările expiră după {@code app.temp-store.ttl}.
 */
public interface TempImageStore {

    /**
     * Salvează imaginea încărcată, cu TTL.
     */
    void putSource(long jobId, byte[] data, String mimeType);

    /**
     * Citește imaginea încărcată, dacă mai există în cache.
     */
    Optional<StoredImage> getSource(long jobId);

    /**
     * Salvează imaginea generată, cu TTL.
     */
    void putResult(long jobId, byte[] data, String mimeType);

    /**
     * Citește imaginea generată, dacă mai există în cache.
     */
    Optional<StoredImage> getResult(long jobId);

    /**
     * Elimină imediat ambele imagini ale unui job.
     */
    void evict(long jobId);

    /**
     * Conținutul binar și tipul MIME al unei imagini din cache.
     */
    record StoredImage(byte[] data, String mimeType) {
    }
}
