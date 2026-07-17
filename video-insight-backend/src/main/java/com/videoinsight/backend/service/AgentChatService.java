package com.videoinsight.backend.service;

import com.videoinsight.backend.model.agent.AgentAnswer;
import com.videoinsight.backend.model.agent.AgentPlan;
import com.videoinsight.backend.model.agent.CriticVerdict;
import com.videoinsight.backend.model.agent.VideoChunk;
import com.videoinsight.backend.model.agent.VideoContextData;

import java.util.List;

/** Planner / Executor / Critic / Chunk 摘要四类 LLM 调用,统一 JSON 输出 + 指数退避。 */
public interface AgentChatService {

    AgentPlan plan(String goal, VideoContextData context);

    AgentAnswer execute(String goal, VideoContextData context, AgentPlan plan, CriticVerdict previousCritique);

    CriticVerdict critique(String goal, VideoContextData context, AgentPlan plan, AgentAnswer answer);

    VideoChunk.ChunkSummary summarizeChunk(List<VideoContextData.VideoSegment> segments);
}
