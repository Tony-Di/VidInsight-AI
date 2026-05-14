package com.videoinsight.backend.service;

import com.videoinsight.backend.common.PageResult;
import com.videoinsight.backend.entity.VideoInfo;

import java.util.Optional;
import java.util.function.Supplier;

public interface VideoCacheService {

    /**
     * 三态返回:
     * <ul>
     *   <li>{@code null}                — 缓存未命中,调用方需要回源数据库</li>
     *   <li>{@code Optional.empty()}    — 命中"不存在"哨兵,调用方应直接返回 null,不要回源</li>
     *   <li>{@code Optional.of(vi)}     — 命中真实数据</li>
     * </ul>
     */
    Optional<VideoInfo> getDetail(Long id);

    /**
     * 防击穿版本:缓存命中直接返回;miss 时用分布式锁互斥回源,
     * 保证同一时刻只有一个线程执行 dbLoader,其它线程等结果。
     */
    VideoInfo getDetailOrLoad(Long id, Supplier<VideoInfo> dbLoader);

    void setDetail(Long id, VideoInfo videoInfo);

    /**
     * 写入"不存在"哨兵,用于防止缓存穿透。TTL 比真实数据更短。
     */
    void markDetailMissing(Long id);

    void evictDetail(Long id);

    PageResult<VideoInfo> getList(int page, int pageSize);

    void setList(int page, int pageSize, PageResult<VideoInfo> result);
}
