package com.videoinsight.backend.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request body for initializing a chunk upload task")
public class ChunkUploadInitRequest {

    @NotBlank(message = "title is required")
    @Schema(description = "Video title.", example = "Large upload video")
    private String title;

    @NotBlank(message = "fileName is required")
    @Schema(description = "Original file name.", example = "large-video.mp4")
    private String fileName;

    @NotNull(message = "totalChunks is required")
    @Min(value = 1, message = "totalChunks must be greater than 0")
    @Schema(description = "Total number of chunks the client will upload.", example = "12")
    private Integer totalChunks;
}
