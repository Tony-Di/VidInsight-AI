package com.videoinsight.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videoinsight.backend.entity.AgentAnalysisTask;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.enums.AgentTaskStatus;
import com.videoinsight.backend.mapper.AgentAnalysisTaskMapper;
import com.videoinsight.backend.mapper.VideoInfoMapper;
import com.videoinsight.backend.model.agent.AgentAnswer;
import com.videoinsight.backend.model.agent.AgentLoopOutcome;
import com.videoinsight.backend.model.agent.AgentPlan;
import com.videoinsight.backend.model.agent.CriticVerdict;
import com.videoinsight.backend.model.agent.VideoContextData;
import com.videoinsight.backend.service.impl.AgentAnalysisJobServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 注意:实现里 updateById 会被调用两次(先标 PROCESSING,再写终态),
// 所以用 ArgumentCaptor 取第二次调用的最终状态断言,不能用一次性的 argThat。
@ExtendWith(MockitoExtension.class)
class AgentAnalysisJobServiceTest {

    @Mock
    private AgentAnalysisTaskMapper taskMapper;

    @Mock
    private VideoInfoMapper videoInfoMapper;

    @Mock
    private VideoAgentContextService contextService;

    @Mock
    private VideoAgentLoopService loopService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    private AgentAnalysisJobServiceImpl jobService;

    @BeforeEach
    void setUp() {
        jobService = new AgentAnalysisJobServiceImpl(taskMapper, videoInfoMapper,
                contextService, loopService, redissonClient, new ObjectMapper());
    }

    private AgentAnalysisTask task(AgentTaskStatus status) {
        AgentAnalysisTask task = new AgentAnalysisTask();
        task.setId(7L);
        task.setVideoId(1L);
        task.setUserId(42L);
        task.setGoal("总结重点");
        task.setGoalDigest("abc123");
        task.setStatus(status);
        return task;
    }

    @Test
    void completedTaskIsSkippedWithoutRunningLoop() {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(taskMapper.selectById(7L)).thenReturn(task(AgentTaskStatus.COMPLETED));

        jobService.execute(7L);

        verify(loopService, never()).run(anyLong(), anyString(), any());
    }

    @Test
    void lockContentionSkipsQuietly() {
        // 实现会先 selectById 一次拿 videoId+goalDigest 组锁 key,这次读是预期内的
        when(taskMapper.selectById(7L)).thenReturn(task(AgentTaskStatus.PENDING));
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(false);

        jobService.execute(7L);

        verify(loopService, never()).run(anyLong(), anyString(), any());
        // any(Class):BaseMapper 3.5.9 的 updateById 有 T 和 Collection<T> 两个重载,裸 any() 编译歧义
        verify(taskMapper, never()).updateById(any(AgentAnalysisTask.class));
    }

    @Test
    void happyPathPersistsCompletedTaskWithJson() {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(taskMapper.selectById(7L)).thenReturn(task(AgentTaskStatus.PENDING));
        VideoInfo video = new VideoInfo();
        video.setId(1L);
        video.setSourceUrl("/uploads/videos/a.mp4");
        when(videoInfoMapper.selectById(1L)).thenReturn(video);
        VideoContextData context = new VideoContextData("src", List.of(
                new VideoContextData.VideoSegment(0, 60_000, "hi", List.of(), List.of())));
        when(contextService.getOrBuild(video)).thenReturn(context);
        when(loopService.run(anyLong(), anyString(), any())).thenReturn(new AgentLoopOutcome(
                new AgentPlan("g", List.of("t")),
                new AgentAnswer("t", List.of("c"),
                        List.of(new AgentAnswer.Evidence(1000, "ASR", "hi")), List.of()),
                new CriticVerdict(true, List.of(), List.of(), List.of(), List.of()), 1));

        jobService.execute(7L);

        ArgumentCaptor<AgentAnalysisTask> captor = ArgumentCaptor.forClass(AgentAnalysisTask.class);
        verify(taskMapper, times(2)).updateById(captor.capture()); // PROCESSING + COMPLETED
        AgentAnalysisTask saved = captor.getAllValues().get(1);
        assertEquals(AgentTaskStatus.COMPLETED, saved.getStatus());
        assertNotNull(saved.getPlanJson());
        assertNotNull(saved.getAnswerJson());
        assertEquals(1, saved.getRoundCount());
    }

    @Test
    void loopFailureMarksTaskFailedAndDoesNotThrow() {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(taskMapper.selectById(7L)).thenReturn(task(AgentTaskStatus.PENDING));
        VideoInfo video = new VideoInfo();
        video.setId(1L);
        video.setSourceUrl("/uploads/videos/a.mp4");
        when(videoInfoMapper.selectById(1L)).thenReturn(video);
        when(contextService.getOrBuild(video)).thenThrow(new IllegalStateException("ASR 与 OCR 分支均失败"));

        jobService.execute(7L);

        ArgumentCaptor<AgentAnalysisTask> captor = ArgumentCaptor.forClass(AgentAnalysisTask.class);
        verify(taskMapper, times(2)).updateById(captor.capture()); // PROCESSING + FAILED
        AgentAnalysisTask saved = captor.getAllValues().get(1);
        assertEquals(AgentTaskStatus.FAILED, saved.getStatus());
        assertNotNull(saved.getErrorMessage());
    }
}
