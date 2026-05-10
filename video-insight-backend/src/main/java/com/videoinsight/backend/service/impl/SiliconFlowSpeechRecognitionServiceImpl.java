package com.videoinsight.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videoinsight.backend.config.SiliconFlowProperties;
import com.videoinsight.backend.service.FileStorageService;
import com.videoinsight.backend.service.SpeechRecognitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SiliconFlowSpeechRecognitionServiceImpl implements SpeechRecognitionService {

    private final FileStorageService fileStorageService;

    private final ObjectMapper objectMapper;

    private final SiliconFlowProperties siliconFlowProperties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public String transcribe(String audioUrl) {
        if (!StringUtils.hasText(siliconFlowProperties.getApiKey())) {
            throw new IllegalStateException("SILICONFLOW_API_KEY is not configured");
        }

        Path audioPath = fileStorageService.resolveLocalPath(audioUrl);
        String boundary = "----VidInsightBoundary" + UUID.randomUUID();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(siliconFlowProperties.getBaseUrl() + "/audio/transcriptions"))
                    .timeout(Duration.ofMinutes(10))
                    .header("Authorization", "Bearer " + siliconFlowProperties.getApiKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(buildMultipartBody(boundary, audioPath)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("ASR request failed: " + response.statusCode() + " - " + response.body());
            }

            JsonNode jsonNode = objectMapper.readTree(response.body());
            JsonNode textNode = jsonNode.get("text");
            if (textNode == null || !StringUtils.hasText(textNode.asText())) {
                throw new IllegalStateException("ASR response does not contain text");
            }
            return textNode.asText();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to call ASR service: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ASR request was interrupted", exception);
        }
    }

    private byte[] buildMultipartBody(String boundary, Path audioPath) throws IOException {
        String fileName = audioPath.getFileName().toString();
        String lineSeparator = "\r\n";
        String prefix = "--" + boundary + lineSeparator
                + "Content-Disposition: form-data; name=\"model\"" + lineSeparator
                + lineSeparator
                + siliconFlowProperties.getAsrModel() + lineSeparator
                + "--" + boundary + lineSeparator
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + lineSeparator
                + "Content-Type: application/octet-stream" + lineSeparator
                + lineSeparator;
        String suffix = lineSeparator + "--" + boundary + "--" + lineSeparator;

        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] fileBytes = Files.readAllBytes(audioPath);
        byte[] suffixBytes = suffix.getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[prefixBytes.length + fileBytes.length + suffixBytes.length];

        System.arraycopy(prefixBytes, 0, body, 0, prefixBytes.length);
        System.arraycopy(fileBytes, 0, body, prefixBytes.length, fileBytes.length);
        System.arraycopy(suffixBytes, 0, body, prefixBytes.length + fileBytes.length, suffixBytes.length);

        return body;
    }
}
