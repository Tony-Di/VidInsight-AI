package com.videoinsight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videoinsight.backend.entity.VideoAgentContext;
import com.videoinsight.backend.mapper.VideoAgentContextMapper;
import com.videoinsight.backend.model.agent.CriticVerdict;
import com.videoinsight.backend.model.agent.VideoChunk;
import com.videoinsight.backend.model.agent.VideoContextData;
import com.videoinsight.backend.service.AgentChatService;
import com.videoinsight.backend.service.AgentRetrievalService;
import com.videoinsight.backend.service.EmbeddingService;
import com.videoinsight.backend.util.RetrievalScoreUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRetrievalServiceImpl implements AgentRetrievalService {

    private static final long CHUNK_MS = 5 * 60 * 1000L;

    private static final int TOP_K = 3;

    private final AgentChatService agentChatService;

    private final EmbeddingService embeddingService;

    private final VideoAgentContextMapper contextMapper;

    private final ObjectMapper objectMapper;

    @Override
    public VideoContextData selectRelevant(Long videoId, String goal, VideoContextData context) {
        List<VideoContextData.VideoSegment> segments = context.segments();
        if (segments.isEmpty() || segments.get(segments.size() - 1).endMs() <= CHUNK_MS) {
            return context; // 短视频不需要检索
        }
        List<VideoChunk> chunks = loadChunks(videoId);
        if (chunks == null || chunks.isEmpty()) {
            chunks = buildChunks(segments);
            saveChunks(videoId, chunks);
        }
        List<Double> queryEmbedding = safeEmbed(goal);
        List<VideoContextData.VideoSegment> selected = chunks.stream()
                .sorted(Comparator.comparingDouble((VideoChunk chunk) ->
                        RetrievalScoreUtil.hybridScore(
                                RetrievalScoreUtil.cosine(queryEmbedding, chunk.embedding()),
                                RetrievalScoreUtil.keywordScore(goal, chunk.keywords()))).reversed())
                .limit(TOP_K)
                .flatMap(chunk -> chunk.rawSegments().stream())
                .sorted(Comparator.comparingLong(VideoContextData.VideoSegment::startMs))
                .toList();
        return new VideoContextData(context.source(), selected);
    }

    @Override
    public VideoContextData refineForCritique(Long videoId, String goal, VideoContextData fullContext,
                                              VideoContextData selectedContext, CriticVerdict critique) {
        Map<String, VideoContextData.VideoSegment> merged = new LinkedHashMap<>();
        selectedContext.segments().forEach(segment -> merged.put(key(segment), segment));

        List<Long> requiredTimestamps = critique == null || critique.requiredTimestamps() == null
                ? List.of() : critique.requiredTimestamps();
        fullContext.segments().stream()
                .filter(segment -> requiredTimestamps.stream()
                        .anyMatch(timestamp -> nearSegment(timestamp, segment)))
                .forEach(segment -> merged.put(key(segment), segment));

        VideoContextData retry = selectRelevant(videoId, critiqueQuery(goal, critique), fullContext);
        retry.segments().forEach(segment -> merged.put(key(segment), segment));

        List<VideoContextData.VideoSegment> sorted = merged.values().stream()
                .sorted(Comparator.comparingLong(VideoContextData.VideoSegment::startMs))
                .toList();
        return new VideoContextData(fullContext.source(), sorted);
    }

    private List<VideoChunk> buildChunks(List<VideoContextData.VideoSegment> segments) {
        List<VideoChunk> chunks = new ArrayList<>();
        long lastStart = segments.get(segments.size() - 1).startMs();
        for (long start = 0; start <= lastStart; start += CHUNK_MS) {
            long end = start + CHUNK_MS;
            long chunkStart = start;
            List<VideoContextData.VideoSegment> raw = segments.stream()
                    .filter(segment -> segment.startMs() >= chunkStart && segment.startMs() < end)
                    .toList();
            if (raw.isEmpty()) {
                continue;
            }
            VideoChunk.ChunkSummary summary = safeSummarize(raw);
            List<Double> embedding = safeEmbed(summary.summary() + "\n" + String.join(" ", summary.keywords()));
            chunks.add(new VideoChunk(start, end, summary.summary(), summary.keywords(), raw, embedding));
        }
        return chunks;
    }

    /** chunk 摘要失败 → 原文截断 500 字兜底,检索退化但不中断。 */
    private VideoChunk.ChunkSummary safeSummarize(List<VideoContextData.VideoSegment> segments) {
        try {
            return agentChatService.summarizeChunk(segments);
        } catch (RuntimeException exception) {
            log.warn("chunk_summary_fallback", exception);
            String raw = segments.stream()
                    .map(segment -> segment.transcript() + " " + String.join(" ", segment.ocrTexts()))
                    .reduce("", (a, b) -> a + " " + b).trim();
            return new VideoChunk.ChunkSummary(raw.length() <= 500 ? raw : raw.substring(0, 500), List.of());
        }
    }

    /** Embedding 失败 → 空向量,余弦为 0,退化成纯关键词打分。 */
    private List<Double> safeEmbed(String text) {
        try {
            return embeddingService.embed(text);
        } catch (RuntimeException exception) {
            log.warn("embedding_fallback", exception);
            return List.of();
        }
    }

    private List<VideoChunk> loadChunks(Long videoId) {
        if (videoId == null) {
            return null;
        }
        VideoAgentContext row = contextMapper.selectOne(new LambdaQueryWrapper<VideoAgentContext>()
                .eq(VideoAgentContext::getVideoId, videoId));
        if (row == null || row.getChunksJson() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(row.getChunksJson(), new TypeReference<List<VideoChunk>>() {});
        } catch (Exception exception) {
            log.warn("chunks_json deserialize failed, rebuilding. videoId={}", videoId, exception);
            return null;
        }
    }

    private void saveChunks(Long videoId, List<VideoChunk> chunks) {
        if (videoId == null) {
            return;
        }
        try {
            VideoAgentContext row = contextMapper.selectOne(new LambdaQueryWrapper<VideoAgentContext>()
                    .eq(VideoAgentContext::getVideoId, videoId));
            if (row == null) {
                return; // 上下文行不存在时不落索引(理论上不会发生:selectRelevant 前必先 getOrBuild)
            }
            row.setChunksJson(objectMapper.writeValueAsString(chunks));
            row.setUpdatedAt(LocalDateTime.now());
            contextMapper.updateById(row);
        } catch (Exception exception) {
            log.warn("chunks_json persist failed, retrieval will rebuild next time. videoId={}", videoId, exception);
        }
    }

    private String critiqueQuery(String goal, CriticVerdict critique) {
        if (critique == null) {
            return goal;
        }
        return String.join("\n", goal,
                String.join(" ", critique.feedback() == null ? List.of() : critique.feedback()),
                String.join(" ", critique.missingRequirements() == null ? List.of() : critique.missingRequirements()),
                String.join(" ", critique.unsupportedClaims() == null ? List.of() : critique.unsupportedClaims()));
    }

    private boolean nearSegment(long timestamp, VideoContextData.VideoSegment segment) {
        long margin = Math.max(60_000L, segment.endMs() - segment.startMs());
        return timestamp >= Math.max(0, segment.startMs() - margin)
                && timestamp < segment.endMs() + margin;
    }

    private String key(VideoContextData.VideoSegment segment) {
        return segment.startMs() + ":" + segment.endMs();
    }
}
