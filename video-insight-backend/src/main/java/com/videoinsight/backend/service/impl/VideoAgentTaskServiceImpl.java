package com.videoinsight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videoinsight.backend.entity.AgentAnalysisTask;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.enums.AgentTaskStatus;
import com.videoinsight.backend.enums.VideoStatus;
import com.videoinsight.backend.exception.BusinessException;
import com.videoinsight.backend.mapper.AgentAnalysisTaskMapper;
import com.videoinsight.backend.mapper.VideoInfoMapper;
import com.videoinsight.backend.model.agent.AgentAnswer;
import com.videoinsight.backend.model.agent.AgentPlan;
import com.videoinsight.backend.model.agent.CriticVerdict;
import com.videoinsight.backend.model.response.AgentTaskResponse;
import com.videoinsight.backend.mq.AgentAnalysisProducer;
import com.videoinsight.backend.security.SecurityUtil;
import com.videoinsight.backend.service.VideoAgentTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoAgentTaskServiceImpl implements VideoAgentTaskService {

    private final AgentAnalysisTaskMapper taskMapper;

    private final VideoInfoMapper videoInfoMapper;

    private final AgentAnalysisProducer agentAnalysisProducer;

    private final ObjectMapper objectMapper;

    @Override
    public AgentTaskResponse create(Long videoId, String goal) {
        VideoInfo video = requireOwnedVideo(videoId);
        if (video.getVideoStatus() != VideoStatus.COMPLETED) {
            throw new BusinessException(400, "video analysis is not completed yet");
        }
        String normalizedGoal = goal.trim();
        String digest = DigestUtils.md5DigestAsHex(normalizedGoal.getBytes(StandardCharsets.UTF_8));

        // 同视频同目标的已完成任务直接复用:追问相同问题秒回,不重复扣 AI 费
        AgentAnalysisTask existing = taskMapper.selectOne(new LambdaQueryWrapper<AgentAnalysisTask>()
                .eq(AgentAnalysisTask::getVideoId, videoId)
                .eq(AgentAnalysisTask::getGoalDigest, digest)
                .eq(AgentAnalysisTask::getStatus, AgentTaskStatus.COMPLETED)
                .orderByDesc(AgentAnalysisTask::getId)
                .last("LIMIT 1"));
        if (existing != null) {
            return toResponse(existing);
        }

        AgentAnalysisTask task = new AgentAnalysisTask();
        task.setVideoId(videoId);
        task.setUserId(SecurityUtil.currentUserId());
        task.setGoal(normalizedGoal);
        task.setGoalDigest(digest);
        task.setStatus(AgentTaskStatus.PENDING);
        task.setRoundCount(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        agentAnalysisProducer.send(task.getId());
        return toResponse(task);
    }

    @Override
    public AgentTaskResponse get(Long taskId) {
        AgentAnalysisTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "agent task not found");
        }
        if (!task.getUserId().equals(SecurityUtil.currentUserId())) {
            throw new BusinessException(403, "not your task");
        }
        return toResponse(task);
    }

    @Override
    public List<AgentTaskResponse> list(Long videoId) {
        requireOwnedVideo(videoId);
        return taskMapper.selectList(new LambdaQueryWrapper<AgentAnalysisTask>()
                        .eq(AgentAnalysisTask::getVideoId, videoId)
                        .orderByDesc(AgentAnalysisTask::getId)
                        .last("LIMIT 20"))
                .stream().map(this::toResponse).toList();
    }

    private VideoInfo requireOwnedVideo(Long videoId) {
        VideoInfo video = videoInfoMapper.selectById(videoId);
        if (video == null) {
            throw new BusinessException(404, "video not found");
        }
        if (!video.getUserId().equals(SecurityUtil.currentUserId())) {
            throw new BusinessException(403, "not your video");
        }
        return video;
    }

    private AgentTaskResponse toResponse(AgentAnalysisTask task) {
        return new AgentTaskResponse(task.getId(), task.getVideoId(), task.getGoal(),
                task.getStatus(), task.getRoundCount(),
                parse(task.getPlanJson(), AgentPlan.class),
                parse(task.getAnswerJson(), AgentAnswer.class),
                parse(task.getCriticJson(), CriticVerdict.class),
                task.getErrorMessage(), task.getCreatedAt(), task.getUpdatedAt());
    }

    private <T> T parse(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception exception) {
            log.warn("agent task json parse failed type={}", type.getSimpleName(), exception);
            return null;
        }
    }
}
