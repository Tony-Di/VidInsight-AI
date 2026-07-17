package com.videoinsight.backend.model.agent;

import java.util.List;

/** Critic 产物:passed=false 时 feedback/requiredTimestamps 驱动下一轮定向补检索。 */
public record CriticVerdict(boolean passed, List<String> feedback,
                            List<String> missingRequirements, List<String> unsupportedClaims,
                            List<Long> requiredTimestamps) {
}
