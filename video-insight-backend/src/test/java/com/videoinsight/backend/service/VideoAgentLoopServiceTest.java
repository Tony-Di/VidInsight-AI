package com.videoinsight.backend.service;

import com.videoinsight.backend.model.agent.AgentAnswer;
import com.videoinsight.backend.model.agent.AgentLoopOutcome;
import com.videoinsight.backend.model.agent.AgentPlan;
import com.videoinsight.backend.model.agent.CriticVerdict;
import com.videoinsight.backend.model.agent.VideoContextData;
import com.videoinsight.backend.service.impl.VideoAgentLoopServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoAgentLoopServiceTest {

    @Mock
    private AgentChatService agentChatService;

    @Mock
    private AgentRetrievalService agentRetrievalService;

    @Mock
    private EvidenceVerifier evidenceVerifier;

    private VideoAgentLoopServiceImpl loopService;

    private final VideoContextData context = new VideoContextData("src", List.of(
            new VideoContextData.VideoSegment(0, 60_000, "hello world", List.of(), List.of())));

    private final AgentPlan plan = new AgentPlan("goal", List.of("t1", "t2", "t3"));

    private final AgentAnswer answer = new AgentAnswer("title", List.of("c1"),
            List.of(new AgentAnswer.Evidence(10_000, "ASR", "hello")), List.of("s1"));

    private static CriticVerdict verdict(boolean passed) {
        return new CriticVerdict(passed, List.of(), List.of(), List.of(), List.of());
    }

    @BeforeEach
    void setUp() {
        loopService = new VideoAgentLoopServiceImpl(agentChatService, agentRetrievalService, evidenceVerifier);
        when(agentRetrievalService.selectRelevant(anyLong(), anyString(), any())).thenReturn(context);
        when(agentChatService.plan(anyString(), any())).thenReturn(plan);
        when(agentChatService.execute(anyString(), any(), any(), any())).thenReturn(answer);
        // lenient:第三个用例会覆盖成 false,严格模式下否则报 UnnecessaryStubbing
        lenient().when(evidenceVerifier.supported(any(), any())).thenReturn(true);
    }

    @Test
    void passesInFirstRoundWithoutRefinement() {
        when(agentChatService.critique(anyString(), any(), any(), any())).thenReturn(verdict(true));

        AgentLoopOutcome outcome = loopService.run(1L, "目标", context);

        assertTrue(outcome.critique().passed());
        assertEquals(1, outcome.rounds());
        verify(agentRetrievalService, never()).refineForCritique(anyLong(), anyString(), any(), any(), any());
    }

    @Test
    void failedCritiqueTriggersRefinementAndSecondRound() {
        when(agentChatService.critique(anyString(), any(), any(), any()))
                .thenReturn(verdict(false))
                .thenReturn(verdict(true));
        when(agentRetrievalService.refineForCritique(anyLong(), anyString(), any(), any(), any()))
                .thenReturn(context);

        AgentLoopOutcome outcome = loopService.run(1L, "目标", context);

        assertTrue(outcome.critique().passed());
        assertEquals(2, outcome.rounds());
        verify(agentChatService, times(2)).execute(anyString(), any(), any(), any());
        verify(agentRetrievalService, times(1)).refineForCritique(anyLong(), anyString(), any(), any(), any());
    }

    @Test
    void fabricatedEvidenceOverridesPassingCritique() {
        // Critic 说通过,但证据核验不认——最终 critique 必须是不通过(防幻觉兜底)
        when(agentChatService.critique(anyString(), any(), any(), any())).thenReturn(verdict(true));
        when(evidenceVerifier.supported(any(), any())).thenReturn(false);
        when(agentRetrievalService.refineForCritique(anyLong(), anyString(), any(), any(), any()))
                .thenReturn(context);

        AgentLoopOutcome outcome = loopService.run(1L, "目标", context);

        assertFalse(outcome.critique().passed());
        assertEquals(2, outcome.rounds(), "第一轮被证据核验打回,第二轮仍失败后返回");
    }
}
