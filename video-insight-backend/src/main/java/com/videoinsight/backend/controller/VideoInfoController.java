package com.videoinsight.backend.controller;

import com.videoinsight.backend.common.ApiResponse;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.model.request.VideoCreateRequest;
import com.videoinsight.backend.service.VideoInfoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoInfoController {

    private final VideoInfoService videoInfoService;

    @PostMapping
    public ApiResponse<VideoInfo> createVideo(@Valid @RequestBody VideoCreateRequest request) {
        return ApiResponse.success(videoInfoService.createVideo(request));
    }

    @GetMapping
    public ApiResponse<List<VideoInfo>> listVideos() {
        return ApiResponse.success(videoInfoService.listVideos());
    }

    @GetMapping("/{id}")
    public ApiResponse<VideoInfo> getVideoDetail(@PathVariable Long id) {
        return ApiResponse.success(videoInfoService.getVideoDetail(id));
    }
}
