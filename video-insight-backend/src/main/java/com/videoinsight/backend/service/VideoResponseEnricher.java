package com.videoinsight.backend.service;

import com.videoinsight.backend.common.PageResult;
import com.videoinsight.backend.entity.VideoInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Populates playUrl / audioPlayUrl on VideoInfo right before it ships to the client.
 * Kept out of the cache layer on purpose: presigned URLs expire, so they must be regenerated
 * per request rather than stored.
 */
@Component
@RequiredArgsConstructor
public class VideoResponseEnricher {

    private final FileStorageService fileStorageService;

    public VideoInfo enrich(VideoInfo video) {
        if (video == null) {
            return null;
        }
        if (video.getSourceUrl() != null) {
            video.setPlayUrl(fileStorageService.publicUrl(video.getSourceUrl()));
        }
        if (video.getAudioUrl() != null) {
            video.setAudioPlayUrl(fileStorageService.publicUrl(video.getAudioUrl()));
        }
        return video;
    }

    public PageResult<VideoInfo> enrich(PageResult<VideoInfo> page) {
        if (page == null || page.getRecords() == null) {
            return page;
        }
        page.getRecords().forEach(this::enrich);
        return page;
    }
}
