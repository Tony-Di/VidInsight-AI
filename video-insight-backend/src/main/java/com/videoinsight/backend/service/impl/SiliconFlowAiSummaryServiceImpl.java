package com.videoinsight.backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videoinsight.backend.config.SiliconFlowProperties;
import com.videoinsight.backend.service.AiSummaryService;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class SiliconFlowAiSummaryServiceImpl implements AiSummaryService {

    private final ObjectMapper objectMapper;

    private final SiliconFlowProperties siliconFlowProperties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public String summarize(String transcript) {
        if (!StringUtils.hasText(siliconFlowProperties.getApiKey())) {
            throw new IllegalStateException("SILICONFLOW_API_KEY is not configured");
        }
        if (!StringUtils.hasText(transcript)) {
            throw new IllegalArgumentException("transcript is empty");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(siliconFlowProperties.getBaseUrl() + "/chat/completions"))
                    .timeout(Duration.ofMinutes(20))
                    .header("Authorization", "Bearer " + siliconFlowProperties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(transcript), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("AI summary request failed: " + response.statusCode() + " - " + response.body());
            }

            JsonNode contentNode = objectMapper.readTree(response.body())
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content");
            if (!StringUtils.hasText(contentNode.asText())) {
                throw new IllegalStateException("AI summary response does not contain content");
            }
            return contentNode.asText();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to build AI summary request", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to call AI summary service: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI summary request was interrupted", exception);
        }
    }

    private String buildRequestBody(String transcript) throws JsonProcessingException {
        String systemPrompt = """
                You are a professional video content analyst.
                Summarize the transcript into clear Markdown with:
                1. Core summary
                2. Key insights
                3. Important quotes if any
                4. Topic tags — output as a single line of inline-code tags separated by spaces, e.g. `#TagOne` `#TagTwo` `#TagThree`. Do NOT use bullet lists or tables for tags.
                Be concise, factual, and structured.
                """;

        Map<String, Object> body = Map.of(
                "model", siliconFlowProperties.getChatModel(),
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", transcript)
                )
        );
        return objectMapper.writeValueAsString(body);
    }
}
