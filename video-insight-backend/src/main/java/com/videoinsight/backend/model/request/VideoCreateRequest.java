package com.videoinsight.backend.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request body for creating a video from URL")
public class VideoCreateRequest {

    @NotBlank(message = "title is required")
    @Schema(description = "Video title.", example = "Test video")
    private String title;

    @NotBlank(message = "sourceUrl is required")
    @Schema(description = "External video URL.", example = "https://example.com/test.mp4")
    private String sourceUrl;
}
