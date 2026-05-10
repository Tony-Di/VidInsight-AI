package com.videoinsight.backend.service;

import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.model.response.VideoAnalysisResult;

public interface VideoAnalysisService {

    VideoAnalysisResult analyze(VideoInfo videoInfo);
}
