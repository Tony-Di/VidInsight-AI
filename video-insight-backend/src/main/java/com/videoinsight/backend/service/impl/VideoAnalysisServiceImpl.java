package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.service.AiSummaryService;
import com.videoinsight.backend.service.MediaProcessingService;
import com.videoinsight.backend.service.SpeechRecognitionService;
import com.videoinsight.backend.service.VideoAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VideoAnalysisServiceImpl implements VideoAnalysisService {

    private final MediaProcessingService mediaProcessingService;

    private final SpeechRecognitionService speechRecognitionService;

    private final AiSummaryService aiSummaryService;

    @Override
    public String analyze(VideoInfo videoInfo) {
        String audioUrl = mediaProcessingService.extractAudio(videoInfo);
        String transcript = speechRecognitionService.transcribe(audioUrl);
        return aiSummaryService.summarize(transcript);
    }
}
