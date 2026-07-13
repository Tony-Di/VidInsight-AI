package com.videoinsight.backend.service;

import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.model.agent.VideoContextData;

public interface VideoAgentContextService {

    /**
     * 读 video_agent_context 缓存,未命中则构建(分段 ASR ∥ 关键帧 OCR,代价高)并持久化。
     * 两分支单路失败可容忍;双路全失败抛 IllegalStateException。
     */
    VideoContextData getOrBuild(VideoInfo videoInfo);
}
