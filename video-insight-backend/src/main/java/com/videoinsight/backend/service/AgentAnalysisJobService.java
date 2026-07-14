package com.videoinsight.backend.service;

public interface AgentAnalysisJobService {

    /** MQ 消费入口:锁 → 幂等检查 → 建上下文 → AgentLoop → 落库。业务失败标 FAILED 不抛出。 */
    void execute(Long taskId);

    /** 死信兜底:未完成的任务标 FAILED,避免前端永远轮询不到终态。 */
    void markDeadLetter(Long taskId);
}
