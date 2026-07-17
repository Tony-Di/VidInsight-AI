package com.videoinsight.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videoinsight.backend.entity.AgentAnalysisTask;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.enums.AgentTaskStatus;
import com.videoinsight.backend.mapper.AgentAnalysisTaskMapper;
import com.videoinsight.backend.mapper.VideoInfoMapper;
import com.videoinsight.backend.model.agent.AgentLoopOutcome;
import com.videoinsight.backend.model.agent.VideoContextData;
import com.videoinsight.backend.service.AgentAnalysisJobService;
import com.videoinsight.backend.service.VideoAgentContextService;
import com.videoinsight.backend.service.VideoAgentLoopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAnalysisJobServiceImpl implements AgentAnalysisJobService {

    private static final String AGENT_LOCK_PREFIX = "vidinsight:lock:agent:";

    private final AgentAnalysisTaskMapper taskMapper;

    private final VideoInfoMapper videoInfoMapper;

    private final VideoAgentContextService videoAgentContextService;

    private final VideoAgentLoopService videoAgentLoopService;

    private final RedissonClient redissonClient;

    private final ObjectMapper objectMapper;

    @Override
    public void execute(Long taskId) {
        // 首次 selectById 只为组锁 key(videoId+goalDigest);若 DB 宕机直接抛出 → DLQ 兜底
        AgentAnalysisTask probe = taskMapper.selectById(taskId);
        if (probe == null) {
            log.warn("Agent task {} does not exist, skipping.", taskId);
            return;
        }
        RLock lock = redissonClient.getLock(
                AGENT_LOCK_PREFIX + probe.getVideoId() + ":" + probe.getGoalDigest());
        // waitTime=0:抢不到说明同视频同问题正被处理,跳过(ACK 不重投)。
        // 不传 leaseTime → WatchDog 自动续租,长任务不会中途丢锁;进程崩溃 ≤30s 后锁自动过期。
        if (!lock.tryLock()) {
            log.info("Agent task {} (video {}) 正被其他 worker 处理,跳过。", taskId, probe.getVideoId());
            return;
        }
        try {
            AgentAnalysisTask task = taskMapper.selectById(taskId);
            if (task == null) {
                return;
            }
            if (task.getStatus() == AgentTaskStatus.COMPLETED) {
                log.info("Agent task {} already completed, skipping duplicate delivery.", taskId);
                return;
            }
            task.setStatus(AgentTaskStatus.PROCESSING);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            try {
                VideoInfo video = videoInfoMapper.selectById(task.getVideoId());
                if (video == null || video.getSourceUrl() == null) {
                    throw new IllegalStateException("video missing or has no source file");
                }
                VideoContextData context = videoAgentContextService.getOrBuild(video);
                AgentLoopOutcome outcome = videoAgentLoopService.run(task.getVideoId(), task.getGoal(), context);

                task.setPlanJson(objectMapper.writeValueAsString(outcome.plan()));
                task.setAnswerJson(objectMapper.writeValueAsString(outcome.answer()));
                task.setCriticJson(objectMapper.writeValueAsString(outcome.critique()));
                task.setRoundCount(outcome.rounds());
                task.setStatus(AgentTaskStatus.COMPLETED);
                task.setErrorMessage(null);
            } catch (Exception exception) {
                log.error("Agent analysis failed, taskId={}", taskId, exception);
                task.setStatus(AgentTaskStatus.FAILED);
                task.setErrorMessage(truncate(rootCauseMessage(exception), 1000));
            }
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public void markDeadLetter(Long taskId) {
        AgentAnalysisTask task = taskMapper.selectById(taskId);
        if (task == null || task.getStatus() == AgentTaskStatus.COMPLETED) {
            return;
        }
        task.setStatus(AgentTaskStatus.FAILED);
        task.setErrorMessage("processing failed after redelivery (dead letter)");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private String rootCauseMessage(Exception exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        if (rootCause.getMessage() == null || rootCause.getMessage().isBlank()) {
            return String.valueOf(exception.getMessage());
        }
        if (rootCause == exception) {
            return rootCause.getMessage();
        }
        return exception.getMessage() + ": " + rootCause.getMessage();
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
