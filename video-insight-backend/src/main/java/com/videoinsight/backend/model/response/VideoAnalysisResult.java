package com.videoinsight.backend.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VideoAnalysisResult {

    private String audioUrl;

    private String transcript;

    private String summary;
}
