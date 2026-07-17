package com.videoinsight.backend.service;

import com.videoinsight.backend.model.agent.AgentLoopOutcome;
import com.videoinsight.backend.model.agent.VideoContextData;

public interface VideoAgentLoopService {

    /**
     * 检索 → Planner → (Executor → Critic → 证据核验)×≤2。
     * critique 未通过也返回结果(前端展示"有保留"标记),answer 缺失才抛异常。
     */
    AgentLoopOutcome run(Long videoId, String goal, VideoContextData context);
}
