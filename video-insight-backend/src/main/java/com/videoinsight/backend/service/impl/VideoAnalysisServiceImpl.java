package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.service.MediaProcessingService;
import com.videoinsight.backend.service.VideoAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VideoAnalysisServiceImpl implements VideoAnalysisService {

    private final MediaProcessingService mediaProcessingService;

    @Override
    public String analyze(VideoInfo videoInfo) {
        String audioUrl = mediaProcessingService.extractAudio(videoInfo);
        return "Audio extracted successfully: " + audioUrl;
    }
}
