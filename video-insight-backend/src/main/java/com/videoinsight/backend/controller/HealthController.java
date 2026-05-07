package com.videoinsight.backend.controller;

import com.videoinsight.backend.common.ApiResponse;
import com.videoinsight.backend.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Health", description = "Application health checks")
public class HealthController {

    private final HealthService healthService;

    @GetMapping("/health")
    @Operation(summary = "Check backend health", description = "Returns ok when the backend application is running.")
    public ApiResponse<String> health() {
        return ApiResponse.success(healthService.check());
    }
}
