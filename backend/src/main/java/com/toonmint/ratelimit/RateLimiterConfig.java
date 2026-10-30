package com.toonmint.ratelimit;

import com.toonmint.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RateLimiterConfig {

    /**
     * Creează implementarea locală, folosită și ca fallback.
     */
    @Bean
    public InMemoryTokenBucketRateLimiter inMemoryTokenBucketRateLimiter() {
        return new InMemoryTokenBucketRateLimiter();
    }

    /**
     * Activează limiter-ul Redis când backend-ul configurat este redis.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.rate-limit.backend", havingValue = "redis")
    public RateLimiter redisRateLimiter(StringRedisTemplate redisTemplate,
                                        InMemoryTokenBucketRateLimiter fallback) {
        return new RedisTokenBucketRateLimiter(redisTemplate, fallback);
    }

    /**
     * Activează limiter-ul local când backend-ul configurat este memory.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.rate-limit.backend", havingValue = "memory", matchIfMissing = true)
    public RateLimiter memoryRateLimiter(InMemoryTokenBucketRateLimiter inMemory) {
        return inMemory;
    }

    /**
     * Expune serviciul de verificare a limitei către restul aplicației.
     */
    @Bean
    public RateLimitService rateLimitService(RateLimiter limiter, AppProperties properties) {
        return new RateLimitService(limiter, properties);
    }
}
