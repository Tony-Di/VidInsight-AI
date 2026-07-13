package com.videoinsight.backend.service;

import com.videoinsight.backend.model.agent.CriticVerdict;
import com.videoinsight.backend.model.agent.VideoContextData;

public interface AgentRetrievalService {

    /**
     * 视频不超过一个 chunk(5 分钟)时原样返回;否则构建/复用 chunk 索引
     * (摘要 + 关键词 + Embedding,持久化在 video_agent_context.chunks_json),
     * 混合打分取 TopK chunk 的原始 segment。
     */
    VideoContextData selectRelevant(Long videoId, String goal, VideoContextData context);

    /**
     * Critic 不通过后的定向补检索:保留已选 segment,补 requiredTimestamps 邻近段,
     * 再用 goal+批评文本 重新检索一轮,合并去重按时间排序。
     */
    VideoContextData refineForCritique(Long videoId, String goal, VideoContextData fullContext,
                                       VideoContextData selectedContext, CriticVerdict critique);
}
