package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.enums.VideoStatus;
import com.videoinsight.backend.mapper.VideoInfoMapper;
import com.videoinsight.backend.service.VideoAnalysisService;
import com.videoinsight.backend.service.VideoAnalysisTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoAnalysisTaskServiceImpl implements VideoAnalysisTaskService {

    private final VideoInfoMapper videoInfoMapper;

    private final VideoAnalysisService videoAnalysisService;

    @Async("analysisTaskExecutor")
    @Override
    public void submitAnalysis(Long videoId) {
        VideoInfo videoInfo = videoInfoMapper.selectById(videoId);
        if (videoInfo == null) {
            log.warn("Video analysis skipped because video {} does not exist.", videoId);
            return;
        }

        try {
            String summary = videoAnalysisService.analyze(videoInfo);
            videoInfo.setVideoStatus(VideoStatus.COMPLETED);
            videoInfo.setSummary(summary);
            videoInfo.setUpdatedAt(LocalDateTime.now());
            videoInfoMapper.updateById(videoInfo);
        } catch (Exception exception) {
            log.error("Video analysis failed, videoId={}", videoId, exception);
            videoInfo.setVideoStatus(VideoStatus.FAILED);
            videoInfo.setSummary(exception.getMessage());
            videoInfo.setUpdatedAt(LocalDateTime.now());
            videoInfoMapper.updateById(videoInfo);
        }
    }
}
