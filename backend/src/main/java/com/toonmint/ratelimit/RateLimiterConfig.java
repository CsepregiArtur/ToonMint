package com.toonmint.ratelimit;

import com.toonmint.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Locale;

@Configuration
public class RateLimiterConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterConfig.class);

    /**
     * Creează implementarea locală, folosită și ca fallback.
     */
    @Bean
    public InMemoryTokenBucketRateLimiter inMemoryTokenBucketRateLimiter() {
        return new InMemoryTokenBucketRateLimiter();
    }

    /**
     * Alege limiter-ul la pornire: Redis când backend-ul configurat este `redis`,
     * altfel bucket-ul local.
     *
     * <p>Condițiile {@code @ConditionalOnProperty} ar fi evaluate de Spring AOT la
     * build, deci binarul nativ ar ignora {@code RATE_LIMIT_BACKEND} de la runtime.
     */
    @Bean
    @Primary
    public RateLimiter rateLimiter(StringRedisTemplate redisTemplate,
                                   InMemoryTokenBucketRateLimiter fallback,
                                   AppProperties properties) {
        String backend = properties.rateLimit().backend() == null
                ? "memory"
                : properties.rateLimit().backend().trim().toLowerCase(Locale.ROOT);
        if ("redis".equals(backend)) {
            log.info("Rate limiter: redis");
            return new RedisTokenBucketRateLimiter(redisTemplate, fallback);
        }
        log.info("Rate limiter: memory");
        return fallback;
    }

    /**
     * Expune serviciul de verificare a limitei către restul aplicației.
     */
    @Bean
    public RateLimitService rateLimitService(RateLimiter limiter, AppProperties properties) {
        return new RateLimitService(limiter, properties);
    }
}
