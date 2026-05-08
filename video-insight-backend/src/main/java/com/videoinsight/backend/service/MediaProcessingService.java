package com.videoinsight.backend.service;

import com.videoinsight.backend.entity.VideoInfo;

public interface MediaProcessingService {

    String extractAudio(VideoInfo videoInfo);
}
