package com.toonmint.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * Token bucket executat atomic pe serverul Redis (fără race conditions între
 * instanțele aplicației). Cade automat pe bucket-ul local când Redis nu răspunde.
 */
public class RedisTokenBucketRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBucketRateLimiter.class);

    /**
     * KEYS[1] bucket key.
     * ARGV[1] capacity, ARGV[2] refill tokens/second, ARGV[3] now (epoch millis), ARGV[4] requested tokens.
     * Returns {allowed(0|1), remaining, retryAfterSeconds}.
     */
    private static final String LUA = """
            local capacity = tonumber(ARGV[1])
            local refill = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])

            local data = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts = tonumber(data[2])
            if tokens == nil then
              tokens = capacity
              ts = now
            end

            local delta = math.max(0, now - ts) / 1000.0
            tokens = math.min(capacity, tokens + delta * refill)

            local ttl = math.ceil((capacity / refill) * 1000) + 1000
            if tokens >= requested then
              tokens = tokens - requested
              redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', now)
              redis.call('PEXPIRE', KEYS[1], ttl)
              return {1, math.floor(tokens), 0}
            end

            local wait = math.ceil((requested - tokens) / refill)
            redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', now)
            redis.call('PEXPIRE', KEYS[1], ttl)
            return {0, math.floor(tokens), wait}
            """;

    private static final RedisScript<List> SCRIPT = new DefaultRedisScript<>(LUA, List.class);

    private final StringRedisTemplate redis;
    private final RateLimiter fallback;

    /**
     * Leagă scriptul Lua de Redis și de limiter-ul de rezervă.
     */
    public RedisTokenBucketRateLimiter(StringRedisTemplate redis, RateLimiter fallback) {
        this.redis = redis;
        this.fallback = fallback;
    }

    /**
     * Execută atomic token bucket-ul în Redis, cu fallback la erori de conexiune.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Decision acquire(String key, int capacity, double refillPerSecond, int requestedTokens) {
        try {
            List<Long> result = redis.execute(
                    SCRIPT,
                    List.of("toonmint:rl:" + key),
                    String.valueOf(capacity),
                    String.valueOf(refillPerSecond),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(requestedTokens));
            if (result == null || result.size() < 3) {
                return fallback.acquire(key, capacity, refillPerSecond, requestedTokens);
            }
            boolean allowed = result.get(0) == 1L;
            long remaining = result.get(1);
            long retryAfter = result.get(2);
            return new Decision(allowed, remaining, retryAfter);
        } catch (RedisConnectionFailureException | IllegalStateException ex) {
            log.warn("Redis unavailable for rate limiting, using in-memory bucket: {}", ex.getMessage());
            return fallback.acquire(key, capacity, refillPerSecond, requestedTokens);
        }
    }
}
