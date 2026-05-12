package com.videoinsight.backend.service;

import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.model.request.VideoImportRequest;

public interface VideoImportService {

    VideoInfo importVideo(VideoImportRequest request);
}
