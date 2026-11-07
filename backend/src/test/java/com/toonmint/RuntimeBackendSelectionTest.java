package com.toonmint;

import com.toonmint.ratelimit.RateLimiter;
import com.toonmint.ratelimit.RedisTokenBucketRateLimiter;
import com.toonmint.storage.RedisTempImageStore;
import com.toonmint.storage.TempImageStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `TEMP_STORE_BACKEND=redis` și `RATE_LIMIT_BACKEND=redis` trebuie să aleagă
 * implementările Redis la pornire. Înainte, alegerea se făcea prin
 * `@ConditionalOnProperty`, adică la build (Spring AOT), și binarul nativ rămânea
 * pe variantele din memorie.
 */
@SpringBootTest(properties = {
        "app.temp-store.backend=redis",
        "app.rate-limit.backend=redis"
})
@ActiveProfiles("test")
class RuntimeBackendSelectionTest {

    @Autowired
    private TempImageStore tempImageStore;

    @Autowired
    private RateLimiter rateLimiter;

    @Test
    void redisBackendsAreSelectedAtRuntime() {
        assertThat(tempImageStore).isInstanceOf(RedisTempImageStore.class);
        assertThat(rateLimiter).isInstanceOf(RedisTokenBucketRateLimiter.class);
    }
}
