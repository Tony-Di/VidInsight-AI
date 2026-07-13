package com.videoinsight.backend.model.agent;

/** AgentLoop 最终产物:计划 + 结构化回答 + 最后一轮 Critic 判定 + 实际轮数。 */
public record AgentLoopOutcome(AgentPlan plan, AgentAnswer answer, CriticVerdict critique, int rounds) {
}
