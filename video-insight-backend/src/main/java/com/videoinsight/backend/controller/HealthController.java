package com.videoinsight.backend.controller;

import com.videoinsight.backend.common.ApiResponse;
import com.videoinsight.backend.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success(healthService.check());
    }
}
