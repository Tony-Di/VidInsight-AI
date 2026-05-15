package com.videoinsight.backend.ratelimit;

import com.videoinsight.backend.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.util.StringUtils;

import java.util.Collections;

@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    private static final String KEY_PREFIX = "vidinsight:ratelimit:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<Long> tokenBucketScript;

    public RateLimitAspect(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        DefaultRedisScript<Long> s = new DefaultRedisScript<>();
        s.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/token_bucket.lua")));
        s.setResultType(Long.class);
        this.tokenBucketScript = s;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String bucketKey = buildKey(rateLimit);

        // 每秒补充率(浮点),例如 5/min = 0.0833/s
        double refillPerSecond = rateLimit.refillPerMinute() / 60.0;

        Long allowed;
        try {
            allowed = redisTemplate.execute(
                    tokenBucketScript,
                    Collections.singletonList(bucketKey),
                    String.valueOf(rateLimit.capacity()),
                    String.valueOf(refillPerSecond),
                    String.valueOf(System.currentTimeMillis())
            );
        } catch (Exception e) {
            // Fail-open:Redis 挂的时候不应该把业务接口也带停,记 warn 让监控发现就行。
            // 安全敏感的场景(银行/付款)应该相反:fail-closed。这里是 NG 项目,选 fail-open。
            log.warn("Rate limit check failed for bucket={}, allowing through. Cause: {}",
                    bucketKey, e.getMessage());
            return pjp.proceed();
        }

        if (allowed == null || allowed == 0L) {
            log.info("Rate limit hit: bucket={}, capacity={}, refill/min={}",
                    bucketKey, rateLimit.capacity(), rateLimit.refillPerMinute());
            throw new RateLimitExceededException(bucketKey, "too many requests, please slow down");
        }
        return pjp.proceed();
    }

    private String buildKey(RateLimit rateLimit) {
        return switch (rateLimit.strategy()) {
            case USER -> KEY_PREFIX + rateLimit.key() + ":user:" + currentUserIdOrThrow();
            case IP -> KEY_PREFIX + rateLimit.key() + ":ip:" + clientIp();
        };
    }

    private Long currentUserIdOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal principal)) {
            // 理论上 USER 策略的端点都在 SecurityConfig 里要求认证,这里走不到。
            // 走到说明配置错了(把 @RateLimit USER 加到了 permitAll 的接口上),fail-closed。
            throw new RateLimitExceededException("", "rate limit requires authentication");
        }
        return principal.userId();
    }

    /**
     * 从 HTTP 请求里解析客户端 IP。优先级:
     *   1. X-Forwarded-For 第一个(经过 nginx/CDN 时真实 IP 在最前面)
     *   2. X-Real-IP
     *   3. ServletRequest.getRemoteAddr()
     * 注意:头部是攻击者可控的,公网部署时只信任已知反向代理设置的头。
     */
    private String clientIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";
        HttpServletRequest req = attrs.getRequest();

        String xff = req.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String xRealIp = req.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) return xRealIp.trim();
        return req.getRemoteAddr();
    }
}
