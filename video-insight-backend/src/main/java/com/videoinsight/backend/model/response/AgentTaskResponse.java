package com.videoinsight.backend.model.response;

import com.videoinsight.backend.enums.AgentTaskStatus;
import com.videoinsight.backend.model.agent.AgentAnswer;
import com.videoinsight.backend.model.agent.AgentPlan;
import com.videoinsight.backend.model.agent.CriticVerdict;

import java.time.LocalDateTime;

public record AgentTaskResponse(Long id, Long videoId, String goal, AgentTaskStatus status,
                                Integer roundCount, AgentPlan plan, AgentAnswer answer,
                                CriticVerdict critic, String errorMessage,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {
}
