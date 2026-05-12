package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.mq.VideoAnalysisProducer;
import com.videoinsight.backend.service.VideoAnalysisTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VideoAnalysisTaskServiceImpl implements VideoAnalysisTaskService {

    private final VideoAnalysisProducer videoAnalysisProducer;

    @Override
    public void submitAnalysis(Long videoId) {
        videoAnalysisProducer.send(videoId);
    }
}
