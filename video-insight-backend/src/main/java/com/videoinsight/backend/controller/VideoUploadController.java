package com.videoinsight.backend.controller;

import com.videoinsight.backend.common.ApiResponse;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.model.request.ChunkUploadInitRequest;
import com.videoinsight.backend.model.response.ChunkUploadInitResponse;
import com.videoinsight.backend.model.response.ChunkUploadResponse;
import com.videoinsight.backend.ratelimit.RateLimit;
import com.videoinsight.backend.service.VideoUploadTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/videos/chunks")
@RequiredArgsConstructor
@Tag(name = "Chunk Upload", description = "Chunked video upload workflow")
public class VideoUploadController {

    private final VideoUploadTaskService videoUploadTaskService;

    @PostMapping("/init")
    @Operation(summary = "Initialize chunk upload", description = "Creates a chunk upload task and returns an uploadId.")
    @RateLimit(key = "video.chunked.init", capacity = 10, refillPerMinute = 10)
    public ApiResponse<ChunkUploadInitResponse> initChunkUpload(@Valid @RequestBody ChunkUploadInitRequest request) {
        return ApiResponse.success(videoUploadTaskService.initChunkUpload(request));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload one chunk", description = "Uploads one chunk file for an existing upload task.")
    public ApiResponse<ChunkUploadResponse> uploadChunk(@Parameter(description = "Upload task id returned by init") @RequestParam("uploadId") String uploadId,
                                                        @Parameter(description = "Chunk index starting from 0") @RequestParam("chunkIndex") Integer chunkIndex,
                                                        @Parameter(description = "Chunk file content") @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(videoUploadTaskService.uploadChunk(uploadId, chunkIndex, file));
    }

    @PostMapping("/complete")
    @Operation(summary = "Complete chunk upload", description = "Validates all chunks, merges them into a video file, and creates a video record.")
    public ApiResponse<VideoInfo> completeChunkUpload(@Parameter(description = "Upload task id returned by init") @RequestParam("uploadId") String uploadId) {
        return ApiResponse.success(videoUploadTaskService.completeChunkUpload(uploadId));
    }
}
