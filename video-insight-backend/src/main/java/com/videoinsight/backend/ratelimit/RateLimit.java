package com.videoinsight.backend.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在 controller 方法上,通过 {@link RateLimitAspect} 切面拦截调用,
 * 走 Redis Lua 令牌桶判定。超限抛 {@link RateLimitExceededException} → HTTP 429。
 *
 * <p>示例:</p>
 * <pre>
 *   @RateLimit(key = "video.import", capacity = 5, refillPerMinute = 5)
 *   public ApiResponse&lt;VideoInfo&gt; importVideo(...) { ... }
 *
 *   @RateLimit(key = "auth.login", capacity = 5, refillPerMinute = 5, strategy = KeyStrategy.IP)
 *   public ApiResponse&lt;AuthResponse&gt; login(...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 桶名(逻辑分组)。同 key 的多个端点共享同一个桶——
     * 比如 import-url 和 retry-import 都用 "video.import",
     * 用户做了 5 次 import 后,retry 也得排队。
     */
    String key();

    /** 桶容量:允许的突发量。 */
    int capacity() default 5;

    /** 每分钟补充的令牌数。配合 capacity 决定长期速率与突发容忍度。 */
    int refillPerMinute() default 5;

    /** 维度:按 userId 还是按 IP。默认 USER(适用于需要认证的接口)。 */
    KeyStrategy strategy() default KeyStrategy.USER;
}
