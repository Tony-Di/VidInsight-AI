package com.videoinsight.backend.service;

import com.videoinsight.backend.model.response.AgentTaskResponse;

import java.util.List;

public interface VideoAgentTaskService {

    /** 校验视频归属与状态后建 PENDING 任务并投递 MQ;同目标已 COMPLETED 则直接复用。 */
    AgentTaskResponse create(Long videoId, String goal);

    AgentTaskResponse get(Long taskId);

    /** 最近 20 条,创建时间倒序。 */
    List<AgentTaskResponse> list(Long videoId);
}
