package com.toonmint.storage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.toonmint.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Store temporar bazat pe Redis, cu fallback local Caffeine, ca o pană de Redis
 * să nu blocheze job-urile deja pornite.
 */
@Component
@ConditionalOnProperty(name = "app.temp-store.backend", havingValue = "redis")
public class RedisTempImageStore implements TempImageStore {

    private static final Logger log = LoggerFactory.getLogger(RedisTempImageStore.class);
    private static final String PREFIX = "toonmint:image:";
    private static final String SEPARATOR = "|";

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final Cache<String, StoredImage> fallback;

    /**
     * Creează store-ul Redis și cache-ul local de rezervă.
     */
    public RedisTempImageStore(StringRedisTemplate redis, AppProperties properties) {
        this.redis = redis;
        this.ttl = properties.tempStore().ttl();
        this.fallback = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(properties.tempStore().maxEntries())
                .build();
    }

    /**
     * Salvează imaginea încărcată în Redis (sau local, dacă Redis e indisponibil).
     */
    @Override
    public void putSource(long jobId, byte[] data, String mimeType) {
        put(InMemoryTempImageStore.sourceKey(jobId), data, mimeType);
    }

    /**
     * Citește imaginea încărcată din Redis, cu fallback local.
     */
    @Override
    public Optional<StoredImage> getSource(long jobId) {
        return get(InMemoryTempImageStore.sourceKey(jobId));
    }

    /**
     * Salvează imaginea generată în Redis (sau local).
     */
    @Override
    public void putResult(long jobId, byte[] data, String mimeType) {
        put(InMemoryTempImageStore.resultKey(jobId), data, mimeType);
    }

    /**
     * Citește imaginea generată din Redis, cu fallback local.
     */
    @Override
    public Optional<StoredImage> getResult(long jobId) {
        return get(InMemoryTempImageStore.resultKey(jobId));
    }

    /**
     * Șterge ambele imagini ale jobului din Redis și din cache-ul local.
     */
    @Override
    public void evict(long jobId) {
        fallback.invalidate(InMemoryTempImageStore.sourceKey(jobId));
        fallback.invalidate(InMemoryTempImageStore.resultKey(jobId));
        try {
            redis.delete(PREFIX + InMemoryTempImageStore.sourceKey(jobId));
            redis.delete(PREFIX + InMemoryTempImageStore.resultKey(jobId));
        } catch (RedisConnectionFailureException ex) {
            log.warn("Redis unavailable while evicting job {} images", jobId);
        }
    }

    /**
     * Scrie valoarea base64 în Redis sau în fallback.
     */
    private void put(String key, byte[] data, String mimeType) {
        String value = mimeType + SEPARATOR + Base64.getEncoder().encodeToString(data);
        try {
            redis.opsForValue().set(PREFIX + key, value, ttl);
        } catch (RedisConnectionFailureException ex) {
            log.warn("Redis unavailable, keeping image {} in the local fallback cache", key);
            fallback.put(key, new StoredImage(data, mimeType));
        }
    }

    /**
     * Citește și decodează o valoare din Redis sau din fallback.
     */
    private Optional<StoredImage> get(String key) {
        try {
            String value = redis.opsForValue().get(PREFIX + key);
            if (value != null) {
                int separator = value.indexOf(SEPARATOR);
                return Optional.of(new StoredImage(
                        Base64.getDecoder().decode(value.substring(separator + 1)),
                        value.substring(0, separator)));
            }
        } catch (RedisConnectionFailureException ex) {
            log.warn("Redis unavailable while reading image {}", key);
        }
        return Optional.ofNullable(fallback.getIfPresent(key));
    }
}
