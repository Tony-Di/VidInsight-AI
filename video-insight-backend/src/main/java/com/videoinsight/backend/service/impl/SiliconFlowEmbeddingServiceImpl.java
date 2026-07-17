package com.videoinsight.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videoinsight.backend.config.SiliconFlowProperties;
import com.videoinsight.backend.service.EmbeddingService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SiliconFlowEmbeddingServiceImpl implements EmbeddingService {

    private final SiliconFlowProperties siliconFlowProperties;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public List<Double> embed(String text) {
        if (!StringUtils.hasText(siliconFlowProperties.getApiKey())) {
            throw new IllegalStateException("SILICONFLOW_API_KEY is not configured");
        }
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("embedding input is empty");
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", siliconFlowProperties.getEmbeddingModel(),
                    "input", text);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(siliconFlowProperties.getBaseUrl() + "/embeddings"))
                    .timeout(Duration.ofMinutes(2))
                    .header("Authorization", "Bearer " + siliconFlowProperties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("embedding request failed: " + response.statusCode()
                        + " - " + response.body());
            }
            JsonNode vector = objectMapper.readTree(response.body())
                    .path("data").path(0).path("embedding");
            if (!vector.isArray() || vector.isEmpty()) {
                throw new IllegalStateException("embedding response contains no vector");
            }
            List<Double> result = new ArrayList<>(vector.size());
            vector.forEach(node -> result.add(node.asDouble()));
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to call embedding service: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("embedding request was interrupted", exception);
        }
    }
}
