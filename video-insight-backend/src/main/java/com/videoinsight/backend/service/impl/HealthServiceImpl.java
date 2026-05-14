package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class HealthServiceImpl implements HealthService {

    private static final String PING_KEY = "vidinsight:health:ping";
    private static final String REDISSON_PROBE_LOCK = "vidinsight:health:redisson:probe";

    private final RedisTemplate<String, Object> redisTemplate;

    private final RedissonClient redissonClient;

    @Override
    public String check() {
        return "ok";
    }

    @Override
    public String checkRedis() {
        String payload = "ping-" + LocalDateTime.now();
        redisTemplate.opsForValue().set(PING_KEY, payload, Duration.ofSeconds(30));
        Object readBack = redisTemplate.opsForValue().get(PING_KEY);
        if (!payload.equals(readBack)) {
            throw new IllegalStateException("redis round-trip mismatch: wrote=" + payload + " read=" + readBack);
        }
        return "ok: " + payload;
    }

    @Override
    public String checkRedisson() {
        // 通过尝试拿一把临时锁验证 Redisson 客户端可达 + 锁机制可用
        RLock lock = redissonClient.getLock(REDISSON_PROBE_LOCK);
        if (!lock.tryLock()) {
            return "warn: probe lock held by another instance";
        }
        try {
            return "ok: acquired & released probe lock; redisson id=" + redissonClient.getId();
        } finally {
            lock.unlock();
        }
    }
}
