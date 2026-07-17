package com.videoinsight.backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videoinsight.backend.config.SiliconFlowProperties;
import com.videoinsight.backend.model.agent.AgentAnswer;
import com.videoinsight.backend.model.agent.AgentPlan;
import com.videoinsight.backend.model.agent.CriticVerdict;
import com.videoinsight.backend.model.agent.VideoChunk;
import com.videoinsight.backend.model.agent.VideoContextData;
import com.videoinsight.backend.service.AgentChatService;
import com.videoinsight.backend.util.LlmJsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SiliconFlowAgentChatServiceImpl implements AgentChatService {

    private static final int MAX_ATTEMPTS = 3;

    private final SiliconFlowProperties siliconFlowProperties;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public SiliconFlowAgentChatServiceImpl(SiliconFlowProperties siliconFlowProperties,
                                           ObjectMapper objectMapper) {
        this.siliconFlowProperties = siliconFlowProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentPlan plan(String goal, VideoContextData context) {
        String prompt = """
                You are the Planner of a video-analysis agent. Understand the user goal and split it
                into 3 to 5 executable tasks. Each task must be answerable using only the ASR text,
                OCR text and timestamps in the VideoContext below.
                Respond in the same language as the user goal.
                Return ONLY JSON:
                {"understoodGoal": "...", "tasks": ["...", "..."]}

                User goal: %s

                VideoContext:
                %s""".formatted(goal, renderContext(context));
        return completeJson(prompt, AgentPlan.class);
    }

    @Override
    public AgentAnswer execute(String goal, VideoContextData context,
                               AgentPlan plan, CriticVerdict previousCritique) {
        String prompt = """
                You are the Executor of a video-analysis agent. Follow the plan and produce a
                structured answer for the user goal, using ONLY facts found in the VideoContext.
                Every important conclusion must be backed by an evidence entry whose timestampMs
                falls inside one of the segment ranges, with source "ASR" or "OCR".
                If a previous critique exists, fix every issue it raises.
                Respond in the same language as the user goal.
                Return ONLY JSON:
                {"title": "...", "conclusions": ["..."],
                 "evidence": [{"timestampMs": 120000, "source": "ASR", "content": "..."}],
                 "suggestions": ["..."]}

                User goal: %s

                Plan: %s

                PreviousCritique: %s

                VideoContext:
                %s""".formatted(goal, toJson(plan), toJson(previousCritique), renderContext(context));
        return completeJson(prompt, AgentAnswer.class);
    }

    @Override
    public CriticVerdict critique(String goal, VideoContextData context,
                                  AgentPlan plan, AgentAnswer answer) {
        String prompt = """
                You are the Critic of a video-analysis agent. You only inspect, never rewrite.
                Checks:
                1. Does the draft cover the user goal and every task in the plan?
                2. Does every important conclusion have timestamp evidence inside the VideoContext?
                3. Are there claims the VideoContext does not support?
                4. Are title / conclusions / evidence / suggestions all present and non-empty?
                passed may only be true if ALL checks pass.
                requiredTimestamps: millisecond positions that need to be re-read in the next round.
                Respond in the same language as the user goal.
                Return ONLY JSON:
                {"passed": false, "feedback": ["..."], "missingRequirements": ["..."],
                 "unsupportedClaims": ["..."], "requiredTimestamps": [120000]}

                User goal: %s

                Plan: %s

                Draft: %s

                VideoContext:
                %s""".formatted(goal, toJson(plan), toJson(answer), renderContext(context));
        return completeJson(prompt, CriticVerdict.class);
    }

    @Override
    public VideoChunk.ChunkSummary summarizeChunk(List<VideoContextData.VideoSegment> segments) {
        String prompt = """
                Compress this five-minute video excerpt. Keep people, events, opinions, conclusions
                and important on-screen (OCR) text. Respond in the language of the excerpt.
                Return ONLY JSON:
                {"summary": "<=200 chars", "keywords": ["k1", "k2", "k3"]}

                Excerpt:
                %s""".formatted(renderContext(new VideoContextData("", segments)));
        return completeJson(prompt, VideoChunk.ChunkSummary.class);
    }

    /**
     * HTTP 失败与 JSON 解析失败都重试(LLM 偶发输出坏 JSON,重新生成一次往往就好),
     * 1s/2s/4s 指数退避,MAX_ATTEMPTS 次封顶。
     */
    private <T> T completeJson(String prompt, Class<T> type) {
        Exception lastError = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                String response = chat(prompt);
                return objectMapper.readValue(LlmJsonUtil.extractJsonObject(response), type);
            } catch (Exception exception) {
                lastError = exception;
                log.warn("agent_llm_attempt_failed attempt={} type={}", attempt + 1, type.getSimpleName(), exception);
                if (attempt < MAX_ATTEMPTS - 1) {
                    sleep(1_000L << attempt);
                }
            }
        }
        throw new IllegalStateException("LLM call failed after " + MAX_ATTEMPTS + " attempts", lastError);
    }

    /** protected:单测里子类覆写以脚本化响应,不发真实 HTTP。 */
    protected String chat(String prompt) {
        if (!StringUtils.hasText(siliconFlowProperties.getApiKey())) {
            throw new IllegalStateException("SILICONFLOW_API_KEY is not configured");
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", siliconFlowProperties.getChatModel(),
                    "stream", false,
                    "messages", List.of(Map.of("role", "user", "content", prompt)));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(siliconFlowProperties.getBaseUrl() + "/chat/completions"))
                    .timeout(Duration.ofMinutes(20))
                    .header("Authorization", "Bearer " + siliconFlowProperties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("LLM request failed: " + response.statusCode()
                        + " - " + response.body());
            }
            JsonNode contentNode = objectMapper.readTree(response.body())
                    .path("choices").path(0).path("message").path("content");
            if (!StringUtils.hasText(contentNode.asText())) {
                throw new IllegalStateException("LLM response does not contain content");
            }
            return contentNode.asText();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to call LLM: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM request was interrupted", exception);
        }
    }

    /** protected:单测里覆写成记录间隔而不是真睡。 */
    protected void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM retry backoff interrupted", exception);
        }
    }

    /**
     * 上下文渲染成紧凑文本而不是原始 JSON(省 token、更易读):
     * [120000ms - 180000ms] (02:00 - 03:00)
     * ASR: ...
     * OCR: ...
     */
    private String renderContext(VideoContextData context) {
        StringBuilder builder = new StringBuilder();
        for (VideoContextData.VideoSegment segment : context.segments()) {
            builder.append("[").append(segment.startMs()).append("ms - ").append(segment.endMs())
                    .append("ms] (").append(formatClock(segment.startMs())).append(" - ")
                    .append(formatClock(segment.endMs())).append(")\n");
            if (segment.transcript() != null && !segment.transcript().isBlank()) {
                builder.append("ASR: ").append(segment.transcript()).append("\n");
            }
            if (!segment.ocrTexts().isEmpty()) {
                builder.append("OCR: ").append(String.join(" | ", segment.ocrTexts())).append("\n");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    private String formatClock(long millis) {
        long totalSeconds = millis / 1000;
        return "%02d:%02d".formatted(totalSeconds / 60, totalSeconds % 60);
    }

    private String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize prompt payload", exception);
        }
    }
}
