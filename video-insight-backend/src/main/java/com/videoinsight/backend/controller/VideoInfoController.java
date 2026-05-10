package com.videoinsight.backend.controller;

import com.videoinsight.backend.common.ApiResponse;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.model.request.VideoCreateRequest;
import com.videoinsight.backend.service.VideoInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
@Tag(name = "Videos", description = "Video records, local upload, and analysis workflow")
public class VideoInfoController {

    private final VideoInfoService videoInfoService;

    @PostMapping
    @Operation(summary = "Create video by URL", description = "Creates a video record from an external video URL.")
    public ApiResponse<VideoInfo> createVideo(@Valid @RequestBody VideoCreateRequest request) {
        return ApiResponse.success(videoInfoService.createVideo(request));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload local video", description = "Uploads a local video file and creates a video record.")
    public ApiResponse<VideoInfo> uploadVideo(@Parameter(description = "Local video file") @RequestPart("file") MultipartFile file,
                                              @Parameter(description = "Optional video title") @RequestParam(value = "title", required = false) String title) {
        return ApiResponse.success(videoInfoService.uploadVideo(file, title));
    }

    @GetMapping
    @Operation(summary = "List videos", description = "Returns all video records ordered by creation time descending.")
    public ApiResponse<List<VideoInfo>> listVideos() {
        return ApiResponse.success(videoInfoService.listVideos());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get video detail", description = "Returns one video record by id.")
    public ApiResponse<VideoInfo> getVideoDetail(@Parameter(description = "Video id") @PathVariable Long id) {
        return ApiResponse.success(videoInfoService.getVideoDetail(id));
    }

    @PostMapping("/{id}/analyze")
    @Operation(summary = "Submit video analysis task", description = "Marks the video as PROCESSING and submits the analysis task to a background thread pool.")
    public ApiResponse<VideoInfo> analyzeVideo(@Parameter(description = "Video id") @PathVariable Long id) {
        return ApiResponse.success(videoInfoService.analyzeVideo(id));
    }
}
