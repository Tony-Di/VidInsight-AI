package com.videoinsight.backend.service;

import com.videoinsight.backend.common.PageResult;
import com.videoinsight.backend.entity.VideoInfo;

import java.util.Optional;

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

    void setDetail(Long id, VideoInfo videoInfo);

    /**
     * 写入"不存在"哨兵,用于防止缓存穿透。TTL 比真实数据更短。
     */
    void markDetailMissing(Long id);

    void evictDetail(Long id);

    PageResult<VideoInfo> getList(int page, int pageSize);

    void setList(int page, int pageSize, PageResult<VideoInfo> result);
}
