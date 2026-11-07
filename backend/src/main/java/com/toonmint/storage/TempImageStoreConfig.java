package com.toonmint.storage;

import com.toonmint.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Locale;

/**
 * Alege implementarea store-ului temporar la pornire, nu la build.
 *
 * <p>Varianta cu {@code @ConditionalOnProperty} pe cele două clase nu funcționează
 * în imaginea nativă: Spring AOT evaluează condițiile la compilare, deci binarul
 * rămâne cu ce a fost ales atunci (profilul {@code dev} din CI = memorie), iar
 * {@code TEMP_STORE_BACKEND=redis} de la runtime este ignorat.
 */
@Configuration
public class TempImageStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(TempImageStoreConfig.class);

    @Bean
    public TempImageStore tempImageStore(AppProperties properties, StringRedisTemplate redisTemplate) {
        String backend = properties.tempStore().backend() == null
                ? "memory"
                : properties.tempStore().backend().trim().toLowerCase(Locale.ROOT);
        if ("redis".equals(backend)) {
            log.info("Temp image store: redis");
            return new RedisTempImageStore(redisTemplate, properties);
        }
        log.info("Temp image store: memory");
        return new InMemoryTempImageStore(properties);
    }
}
