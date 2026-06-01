package com.videoinsight.backend.it;

import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.enums.VideoStatus;
import com.videoinsight.backend.mapper.VideoInfoMapper;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static org.assertj.core.api.Assertions.assertThat;

class Md5DedupIT extends IntegrationTestBase {

    private static final String MD5_LOCK_PREFIX = "vidinsight:lock:upload:md5:";

    @Autowired
    VideoInfoMapper videoInfoMapper;

    @Autowired
    RedissonClient redissonClient;

    private Long seedCompleted(String md5, long userId) {
        VideoInfo v = new VideoInfo();
        v.setUserId(userId);
        v.setTitle("seed");
        v.setSourceUrl("/uploads/videos/seed.mp4");
        v.setFileMd5(md5);
        v.setVideoStatus(VideoStatus.COMPLETED);
        v.setCreatedAt(LocalDateTime.now());
        v.setUpdatedAt(LocalDateTime.now());
        videoInfoMapper.insert(v);
        return v.getId();
    }

    /** Mirrors VideoUploadTaskServiceImpl.upsertWithMd5Lock dedup decision. */
    private VideoInfo dedupDecision(String md5, long userId) {
        RLock lock = redissonClient.getLock(MD5_LOCK_PREFIX + md5);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(10, TimeUnit.SECONDS);
            VideoInfo existing = videoInfoMapper.findCompletedByMd5AndUser(md5, userId);
            if (existing != null) return existing; // reuse, no new row
            VideoInfo created = new VideoInfo();
            created.setUserId(userId);
            created.setFileMd5(md5);
            created.setVideoStatus(VideoStatus.PENDING);
            created.setCreatedAt(LocalDateTime.now());
            created.setUpdatedAt(LocalDateTime.now());
            videoInfoMapper.insert(created);
            return created;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    @Test
    void concurrentDuplicates_reuseExisting_noNewRow() throws Exception {
        String md5 = "abc123def456abc123def456abc12345";
        long userId = 1L;
        Long seededId = seedCompleted(md5, userId);

        CountDownLatch start = new CountDownLatch(1);
        AtomicReferenceArray<VideoInfo> results = new AtomicReferenceArray<>(2);

        Thread t1 = new Thread(() -> { try { start.await(); } catch (InterruptedException ignored) {} results.set(0, dedupDecision(md5, userId)); });
        Thread t2 = new Thread(() -> { try { start.await(); } catch (InterruptedException ignored) {} results.set(1, dedupDecision(md5, userId)); });
        t1.start(); t2.start();
        start.countDown();
        t1.join(15_000); t2.join(15_000);

        assertThat(results.get(0).getId()).isEqualTo(seededId);
        assertThat(results.get(1).getId()).isEqualTo(seededId);

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_info WHERE file_md5 = ?", Long.class, md5);
        assertThat(count).isEqualTo(1L); // only the seeded row; no duplicate created
    }
}
