package com.toonmint.storage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.toonmint.config.AppProperties;

import java.util.Optional;

/**
 * Store temporar în heap, folosit când {@code app.temp-store.backend=memory}.
 * Alegerea se face la runtime, în {@link TempImageStoreConfig}.
 */
public class InMemoryTempImageStore implements TempImageStore {

    private final Cache<String, StoredImage> cache;

    /**
     * Creează cache-ul Caffeine cu TTL și dimensiunea configurate.
     */
    public InMemoryTempImageStore(AppProperties properties) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.tempStore().ttl())
                .maximumSize(properties.tempStore().maxEntries())
                .build();
    }

    /**
     * Stochează imaginea încărcată în cache-ul local.
     */
    @Override
    public void putSource(long jobId, byte[] data, String mimeType) {
        cache.put(sourceKey(jobId), new StoredImage(data, mimeType));
    }

    /**
     * Citește imaginea încărcată din cache-ul local.
     */
    @Override
    public Optional<StoredImage> getSource(long jobId) {
        return Optional.ofNullable(cache.getIfPresent(sourceKey(jobId)));
    }

    /**
     * Stochează imaginea generată în cache-ul local.
     */
    @Override
    public void putResult(long jobId, byte[] data, String mimeType) {
        cache.put(resultKey(jobId), new StoredImage(data, mimeType));
    }

    /**
     * Citește imaginea generată din cache-ul local.
     */
    @Override
    public Optional<StoredImage> getResult(long jobId) {
        return Optional.ofNullable(cache.getIfPresent(resultKey(jobId)));
    }

    /**
     * Invalidează ambele intrări ale jobului.
     */
    @Override
    public void evict(long jobId) {
        cache.invalidate(sourceKey(jobId));
        cache.invalidate(resultKey(jobId));
    }

    /**
     * Cheia cache-ului pentru imaginea încărcată.
     */
    static String sourceKey(long jobId) {
        return "source:" + jobId;
    }

    /**
     * Cheia cache-ului pentru imaginea generată.
     */
    static String resultKey(long jobId) {
        return "result:" + jobId;
    }
}
