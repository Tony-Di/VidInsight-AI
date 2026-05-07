package com.videoinsight.backend.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Response returned after chunk upload initialization")
public class ChunkUploadInitResponse {

    @Schema(description = "Upload task id used for uploading chunks and completing the upload.", example = "2f5b9c38-4e4b-47c1-89d7-5cdd9f81fca6")
    private String uploadId;
}
