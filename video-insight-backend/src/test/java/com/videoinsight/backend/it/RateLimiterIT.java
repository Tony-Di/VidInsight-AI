package com.videoinsight.backend.it;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterIT extends IntegrationTestBase {

    private RedisScript<Long> tokenBucketScript() {
        DefaultRedisScript<Long> s = new DefaultRedisScript<>();
        s.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/token_bucket.lua")));
        s.setResultType(Long.class);
        return s;
    }

    @Test
    void burstOf30_againstCapacity5_allowsExactly5() {
        RedisScript<Long> script = tokenBucketScript();
        String key = "vidinsight:ratelimit:test.bucket:user:1";
        long now = System.currentTimeMillis();

        int allowed = 0;
        int denied = 0;
        for (int i = 0; i < 30; i++) {
            Long res = stringRedisTemplate.execute(
                    script,
                    Collections.singletonList(key),
                    String.valueOf(5),        // capacity
                    String.valueOf(5 / 60.0), // refill per second (~0.083)
                    String.valueOf(now)       // same timestamp => negligible refill
            );
            if (res != null && res == 1L) allowed++; else denied++;
        }

        assertThat(allowed).isEqualTo(5);
        assertThat(denied).isEqualTo(25);
    }
}
