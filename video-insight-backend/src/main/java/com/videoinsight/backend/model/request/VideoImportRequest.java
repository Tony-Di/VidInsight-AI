package com.videoinsight.backend.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Video URL import request")
public class VideoImportRequest {

    @Schema(description = "Optional video title.", example = "Imported video")
    private String title;

    @NotBlank(message = "sourceUrl is required")
    @Schema(description = "Video page or direct media URL.", example = "https://www.youtube.com/watch?v=xxxx")
    private String sourceUrl;
}
