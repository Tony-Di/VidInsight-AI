package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class HealthServiceImpl implements HealthService {

    private static final String PING_KEY = "vidinsight:health:ping";

    private final RedisTemplate<String, Object> redisTemplate;

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
}
