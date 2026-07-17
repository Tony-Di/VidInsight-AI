package com.videoinsight.backend.model.agent;

import java.util.List;

/** Planner 产物:对目标的理解 + 3~5 个可执行子任务。 */
public record AgentPlan(String understoodGoal, List<String> tasks) {
}
