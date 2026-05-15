package com.videoinsight.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.enums.VideoStatus;
import com.videoinsight.backend.exception.BusinessException;
import com.videoinsight.backend.mapper.VideoInfoMapper;
import com.videoinsight.backend.model.request.VideoImportRequest;
import com.videoinsight.backend.service.VideoCacheService;
import com.videoinsight.backend.service.VideoImportService;
import com.videoinsight.backend.service.VideoImportTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VideoImportServiceImpl extends ServiceImpl<VideoInfoMapper, VideoInfo> implements VideoImportService {

    private final VideoImportTaskService videoImportTaskService;

    private final VideoCacheService videoCacheService;

    @Override
    public VideoInfo importVideo(VideoImportRequest request) {
        LocalDateTime now = LocalDateTime.now();

        VideoInfo videoInfo = new VideoInfo();
        videoInfo.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle() : request.getSourceUrl());
        videoInfo.setSourceUrl(request.getSourceUrl());
        videoInfo.setVideoStatus(VideoStatus.IMPORTING);
        videoInfo.setCreatedAt(now);
        videoInfo.setUpdatedAt(now);

        save(videoInfo);
        videoCacheService.evictAllLists();
        videoImportTaskService.submitImport(videoInfo.getId(), request.getSourceUrl());
        return videoInfo;
    }

    @Override
    public VideoInfo retryImport(Long id) {
        VideoInfo videoInfo = getById(id);
        if (videoInfo == null) {
            throw new BusinessException(404, "video does not exist");
        }
        if (videoInfo.getVideoStatus() != VideoStatus.IMPORT_FAILED) {
            throw new BusinessException(400, "only IMPORT_FAILED videos can be retried");
        }
        if (!StringUtils.hasText(videoInfo.getSourceUrl())
                || (!videoInfo.getSourceUrl().startsWith("http://")
                && !videoInfo.getSourceUrl().startsWith("https://"))) {
            throw new BusinessException(400, "original source URL is not available for retry");
        }

        String originalUrl = videoInfo.getSourceUrl();
        videoInfo.setVideoStatus(VideoStatus.IMPORTING);
        videoInfo.setSummary(null);
        videoInfo.setUpdatedAt(LocalDateTime.now());
        updateById(videoInfo);
        videoCacheService.evictDetail(videoInfo.getId());
        videoCacheService.evictAllLists();

        videoImportTaskService.submitImport(videoInfo.getId(), originalUrl);
        return videoInfo;
    }
}
