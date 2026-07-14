package com.videoinsight.backend.controller;

import com.videoinsight.backend.common.ApiResponse;
import com.videoinsight.backend.model.request.AgentAskRequest;
import com.videoinsight.backend.model.response.AgentTaskResponse;
import com.videoinsight.backend.ratelimit.RateLimit;
import com.videoinsight.backend.service.VideoAgentTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Agent", description = "Evidence-constrained video Q&A agent")
public class AgentAnalysisController {

    private final VideoAgentTaskService videoAgentTaskService;

    @PostMapping("/videos/{id}/agent-analyses")
    @Operation(summary = "Ask the video agent",
            description = "Creates a PENDING agent task for the goal and publishes it to the agent queue. Reuses a COMPLETED task with the same goal.")
    @RateLimit(key = "video.agent", capacity = 3, refillPerMinute = 3)
    public ApiResponse<AgentTaskResponse> ask(@Parameter(description = "Video id") @PathVariable Long id,
                                              @Valid @RequestBody AgentAskRequest request) {
        return ApiResponse.success(videoAgentTaskService.create(id, request.getGoal()));
    }

    @GetMapping("/agent-analyses/{taskId}")
    @Operation(summary = "Get agent task", description = "Polling endpoint for agent task status and result.")
    public ApiResponse<AgentTaskResponse> get(@Parameter(description = "Task id") @PathVariable Long taskId) {
        return ApiResponse.success(videoAgentTaskService.get(taskId));
    }

    @GetMapping("/videos/{id}/agent-analyses")
    @Operation(summary = "List agent tasks", description = "Latest 20 agent Q&A tasks of the video, newest first.")
    public ApiResponse<List<AgentTaskResponse>> list(@Parameter(description = "Video id") @PathVariable Long id) {
        return ApiResponse.success(videoAgentTaskService.list(id));
    }
}
