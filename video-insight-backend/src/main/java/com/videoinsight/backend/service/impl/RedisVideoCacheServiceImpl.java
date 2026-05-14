package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.common.PageResult;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.service.VideoCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisVideoCacheServiceImpl implements VideoCacheService {

    private static final String DETAIL_KEY_PREFIX = "vidinsight:video:detail:";
    private static final String LIST_KEY_PREFIX = "vidinsight:video:list:";
    private static final String DETAIL_LOCK_PREFIX = "vidinsight:lock:video:detail:";

    /** 等不到锁的超时时间——避免回源失败时所有线程被无限拖住。 */
    private static final long LOAD_LOCK_WAIT_SECONDS = 5;

    // TTL 基础值 + 随机抖动,防止大量 key 在同一时刻集体失效(缓存雪崩)
    private static final Duration DETAIL_TTL_BASE = Duration.ofMinutes(10);
    private static final Duration LIST_TTL_BASE = Duration.ofSeconds(60);
    private static final Duration MISSING_TTL_BASE = Duration.ofMinutes(2);

    private static final int DETAIL_TTL_JITTER_SECONDS = 120;   // 实际 TTL 在 [10:00, 12:00] 之间
    private static final int LIST_TTL_JITTER_SECONDS = 10;      // 实际 TTL 在 [60s, 70s] 之间
    private static final int MISSING_TTL_JITTER_SECONDS = 30;   // 实际 TTL 在 [2:00, 2:30] 之间

    /** 哨兵值:表示"DB 里也不存在该 id",防穿透。短 TTL,过期后允许回源重试。 */
    private static final String NULL_SENTINEL = "__NULL__";

    private final RedisTemplate<String, Object> redisTemplate;

    private final RedissonClient redissonClient;

    @Override
    public Optional<VideoInfo> getDetail(Long id) {
        try {
            Object cached = redisTemplate.opsForValue().get(detailKey(id));
            if (cached == null) {
                return null;
            }
            if (NULL_SENTINEL.equals(cached)) {
                return Optional.empty();
            }
            return Optional.of((VideoInfo) cached);
        } catch (Exception e) {
            log.warn("Redis getDetail failed for id={}, fall back to DB: {}", id, e.getMessage());
            return null;
        }
    }

    @Override
    public VideoInfo getDetailOrLoad(Long id, Supplier<VideoInfo> dbLoader) {
        // 1) 第一次查缓存
        Optional<VideoInfo> cached = getDetail(id);
        if (cached != null) {
            return cached.orElse(null);
        }

        // 2) cache miss,拿分布式锁互斥回源(防击穿)
        RLock lock = redissonClient.getLock(DETAIL_LOCK_PREFIX + id);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(LOAD_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                // 拿不到锁(等了 5 秒还有别人在跑)→ 降级:跳过缓存直接打 DB
                log.warn("Failed to acquire detail load lock for id={}, fall back to direct DB read", id);
                return dbLoader.get();
            }

            // 3) 双重检查:可能在等锁期间,别的线程已经回源并写好了缓存
            cached = getDetail(id);
            if (cached != null) {
                return cached.orElse(null);
            }

            // 4) 真正回源 DB
            VideoInfo db = dbLoader.get();
            if (db != null) {
                setDetail(id, db);
            } else {
                markDetailMissing(id);
            }
            return db;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for detail load lock id={}, fall back to direct DB read", id);
            return dbLoader.get();
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public void setDetail(Long id, VideoInfo videoInfo) {
        try {
            redisTemplate.opsForValue().set(detailKey(id), videoInfo, jitter(DETAIL_TTL_BASE, DETAIL_TTL_JITTER_SECONDS));
        } catch (Exception e) {
            log.warn("Redis setDetail failed for id={}: {}", id, e.getMessage());
        }
    }

    @Override
    public void markDetailMissing(Long id) {
        try {
            redisTemplate.opsForValue().set(detailKey(id), NULL_SENTINEL, jitter(MISSING_TTL_BASE, MISSING_TTL_JITTER_SECONDS));
        } catch (Exception e) {
            log.warn("Redis markDetailMissing failed for id={}: {}", id, e.getMessage());
        }
    }

    @Override
    public void evictDetail(Long id) {
        try {
            redisTemplate.delete(detailKey(id));
        } catch (Exception e) {
            log.warn("Redis evictDetail failed for id={}: {}", id, e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public PageResult<VideoInfo> getList(int page, int pageSize) {
        try {
            return (PageResult<VideoInfo>) redisTemplate.opsForValue().get(listKey(page, pageSize));
        } catch (Exception e) {
            log.warn("Redis getList failed for page={} size={}, fall back to DB: {}",
                    page, pageSize, e.getMessage());
            return null;
        }
    }

    @Override
    public void setList(int page, int pageSize, PageResult<VideoInfo> result) {
        try {
            redisTemplate.opsForValue().set(listKey(page, pageSize), result, jitter(LIST_TTL_BASE, LIST_TTL_JITTER_SECONDS));
        } catch (Exception e) {
            log.warn("Redis setList failed for page={} size={}: {}", page, pageSize, e.getMessage());
        }
    }

    private String detailKey(Long id) {
        return DETAIL_KEY_PREFIX + id;
    }

    private String listKey(int page, int pageSize) {
        return LIST_KEY_PREFIX + page + ":" + pageSize;
    }

    /** 在 base 上加 [0, jitterSeconds] 之间的随机秒数,防止大量 key 同时过期。 */
    private Duration jitter(Duration base, int jitterSeconds) {
        return base.plusSeconds(ThreadLocalRandom.current().nextInt(jitterSeconds + 1));
    }
}
