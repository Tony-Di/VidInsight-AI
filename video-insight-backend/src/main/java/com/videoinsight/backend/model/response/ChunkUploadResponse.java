package com.videoinsight.backend.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Response returned after uploading one chunk")
public class ChunkUploadResponse {

    @Schema(description = "Upload task id.", example = "2f5b9c38-4e4b-47c1-89d7-5cdd9f81fca6")
    private String uploadId;

    @Schema(description = "Number of chunks uploaded so far.", example = "3")
    private Integer uploadedChunks;

    @Schema(description = "Total number of chunks expected.", example = "12")
    private Integer totalChunks;
}
