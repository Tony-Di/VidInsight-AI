package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.model.agent.AgentAnswer;
import com.videoinsight.backend.model.agent.AgentLoopOutcome;
import com.videoinsight.backend.model.agent.AgentPlan;
import com.videoinsight.backend.model.agent.CriticVerdict;
import com.videoinsight.backend.model.agent.VideoContextData;
import com.videoinsight.backend.service.AgentChatService;
import com.videoinsight.backend.service.AgentRetrievalService;
import com.videoinsight.backend.service.EvidenceVerifier;
import com.videoinsight.backend.service.VideoAgentLoopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoAgentLoopServiceImpl implements VideoAgentLoopService {

    private static final int MAX_ROUNDS = 2;

    private final AgentChatService agentChatService;

    private final AgentRetrievalService agentRetrievalService;

    private final EvidenceVerifier evidenceVerifier;

    @Override
    public AgentLoopOutcome run(Long videoId, String goal, VideoContextData context) {
        if (goal == null || goal.isBlank() || context == null || context.segments().isEmpty()) {
            throw new IllegalArgumentException("agent loop needs a goal and at least one segment");
        }
        VideoContextData relevant = agentRetrievalService.selectRelevant(videoId, goal, context);

        AgentPlan plan = agentChatService.plan(goal, relevant);
        validatePlan(plan);

        AgentAnswer answer = null;
        CriticVerdict critique = null;
        int round = 0;
        while (round < MAX_ROUNDS) {
            round++;
            answer = agentChatService.execute(goal, relevant, plan, critique);
            validateAnswer(answer);
            critique = agentChatService.critique(goal, relevant, plan, answer);
            critique = enforceEvidenceBounds(relevant, answer, critique);
            if (critique.passed()) {
                break;
            }
            log.info("agent_critic_retry videoId={} round={} feedback={}", videoId, round, critique.feedback());
            if (round < MAX_ROUNDS) {
                relevant = agentRetrievalService.refineForCritique(videoId, goal, context, relevant, critique);
            }
        }
        if (answer == null) {
            throw new IllegalStateException("agent produced no answer");
        }
        return new AgentLoopOutcome(plan, answer, critique, round);
    }

    private void validatePlan(AgentPlan plan) {
        if (plan == null || plan.understoodGoal() == null || plan.understoodGoal().isBlank()
                || plan.tasks() == null || plan.tasks().isEmpty() || plan.tasks().size() > 8
                || plan.tasks().stream().anyMatch(task -> task == null || task.isBlank() || task.length() > 500)) {
            throw new IllegalStateException("Planner returned an invalid task list");
        }
    }

    private void validateAnswer(AgentAnswer answer) {
        if (answer == null || answer.title() == null || answer.title().isBlank()
                || answer.conclusions() == null || answer.conclusions().isEmpty()
                || answer.evidence() == null || answer.evidence().isEmpty()) {
            throw new IllegalStateException("Executor returned an incomplete answer");
        }
    }

    /**
     * 防幻觉兜底:Executor 的每条证据回到原始 ASR/OCR 里核验,
     * 核验不过的证据即使 Critic 说通过也强制改判不通过,并把时间戳塞进 requiredTimestamps。
     */
    private CriticVerdict enforceEvidenceBounds(VideoContextData context, AgentAnswer answer,
                                                CriticVerdict critique) {
        if (critique == null) {
            critique = new CriticVerdict(false, List.of("Critic 未返回有效结果"),
                    List.of(), List.of(), List.of());
        }
        List<AgentAnswer.Evidence> invalid = answer.evidence().stream()
                .filter(evidence -> !evidenceVerifier.supported(context, evidence))
                .toList();
        if (invalid.isEmpty()) {
            return critique;
        }
        List<String> unsupported = new ArrayList<>(safeList(critique.unsupportedClaims()));
        invalid.forEach(evidence ->
                unsupported.add("证据无法在原始 ASR/OCR 中核验: " + evidence.timestampMs()));
        List<String> feedback = new ArrayList<>(safeList(critique.feedback()));
        feedback.add("重新检索并绑定有效时间戳证据");
        List<Long> requiredTimestamps = new ArrayList<>(safeList(critique.requiredTimestamps()));
        invalid.stream().map(AgentAnswer.Evidence::timestampMs)
                .filter(timestamp -> !requiredTimestamps.contains(timestamp))
                .forEach(requiredTimestamps::add);
        return new CriticVerdict(false, feedback,
                safeList(critique.missingRequirements()), unsupported, requiredTimestamps);
    }

    private <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }
}
