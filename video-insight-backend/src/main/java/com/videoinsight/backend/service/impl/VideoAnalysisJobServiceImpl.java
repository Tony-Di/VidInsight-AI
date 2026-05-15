package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.enums.VideoStatus;
import com.videoinsight.backend.mapper.VideoInfoMapper;
import com.videoinsight.backend.model.response.VideoAnalysisResult;
import com.videoinsight.backend.service.VideoAnalysisJobService;
import com.videoinsight.backend.service.VideoAnalysisService;
import com.videoinsight.backend.service.VideoCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoAnalysisJobServiceImpl implements VideoAnalysisJobService {

    private final VideoInfoMapper videoInfoMapper;

    private final VideoAnalysisService videoAnalysisService;

    private final VideoCacheService videoCacheService;

    @Override
    public void executeAnalysis(Long videoId) {
        VideoInfo videoInfo = videoInfoMapper.selectById(videoId);
        if (videoInfo == null) {
            log.warn("Video analysis skipped because video {} does not exist.", videoId);
            return;
        }

        // 幂等保护：已完成的任务不重复执行（防止 MQ 重投导致重复分析）
        if (videoInfo.getVideoStatus() == VideoStatus.COMPLETED) {
            log.info("Video {} already completed, skipping duplicate MQ delivery.", videoId);
            return;
        }

        try {
            VideoAnalysisResult result = videoAnalysisService.analyze(videoInfo);
            videoInfo.setVideoStatus(VideoStatus.COMPLETED);
            videoInfo.setAudioUrl(result.getAudioUrl());
            videoInfo.setTranscript(result.getTranscript());
            videoInfo.setSummary(result.getSummary());
            videoInfo.setUpdatedAt(LocalDateTime.now());
            videoInfoMapper.updateById(videoInfo);
            videoCacheService.evictDetail(videoId);
            videoCacheService.evictAllLists();
        } catch (Exception exception) {
            log.error("Video analysis failed, videoId={}", videoId, exception);
            videoInfo.setVideoStatus(VideoStatus.FAILED);
            videoInfo.setSummary(getRootCauseMessage(exception));
            videoInfo.setUpdatedAt(LocalDateTime.now());
            videoInfoMapper.updateById(videoInfo);
            videoCacheService.evictDetail(videoId);
            videoCacheService.evictAllLists();
        }
    }

    private String getRootCauseMessage(Exception exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        if (rootCause.getMessage() == null || rootCause.getMessage().isBlank()) {
            return exception.getMessage();
        }
        if (rootCause == exception) {
            return rootCause.getMessage();
        }
        return exception.getMessage() + ": " + rootCause.getMessage();
    }
}
