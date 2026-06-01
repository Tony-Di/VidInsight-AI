package com.videoinsight.backend.it;

import com.videoinsight.backend.common.PageResult;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.enums.VideoStatus;
import com.videoinsight.backend.service.VideoCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CacheAsideIT extends IntegrationTestBase {

    @Autowired
    VideoCacheService cache;

    private VideoInfo sample(long id) {
        VideoInfo v = new VideoInfo();
        v.setId(id);
        v.setUserId(1L);
        v.setTitle("t" + id);
        v.setVideoStatus(VideoStatus.COMPLETED);
        v.setCreatedAt(LocalDateTime.now());
        v.setUpdatedAt(LocalDateTime.now());
        return v;
    }

    @Test
    void coldKey_isMiss() {
        // null return == cache miss (distinct from Optional.empty == null-sentinel hit)
        assertThat(cache.getDetail(999L)).isNull();
    }

    @Test
    void getDetailOrLoad_populatesThenHits_loaderRunsOnce() {
        AtomicInteger loads = new AtomicInteger();
        VideoInfo first = cache.getDetailOrLoad(1L, () -> { loads.incrementAndGet(); return sample(1L); });
        VideoInfo second = cache.getDetailOrLoad(1L, () -> { loads.incrementAndGet(); return sample(1L); });

        assertThat(first.getId()).isEqualTo(1L);
        assertThat(second.getId()).isEqualTo(1L);
        assertThat(loads.get()).isEqualTo(1);        // 2nd call served from Redis, loader not re-run
        assertThat(cache.getDetail(1L)).isNotNull(); // detail is now cached
    }

    @Test
    void markDetailMissing_returnsEmptyOptional_noDbHit() {
        cache.markDetailMissing(2L);
        Optional<VideoInfo> result = cache.getDetail(2L);
        assertThat(result).isEmpty(); // null-sentinel hit: Optional.empty(), NOT null
    }

    @Test
    void evictUserLists_removesListKeys() {
        // PageResult constructor is (total, page, pageSize, records) — verified against the entity.
        PageResult<VideoInfo> page = new PageResult<>(1L, 1, 10, List.of(sample(1L)));
        cache.setList(1L, 1, 10, page);
        assertThat(cache.getList(1L, 1, 10)).isNotNull();

        cache.evictUserLists(1L);
        assertThat(cache.getList(1L, 1, 10)).isNull(); // SCAN+DEL cleared it
    }
}
