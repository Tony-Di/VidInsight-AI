package com.videoinsight.backend.service;

import com.videoinsight.backend.entity.VideoInfo;

public interface VideoAnalysisService {

    String analyze(VideoInfo videoInfo);
}
