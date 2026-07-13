package com.videoinsight.backend.model.agent;

import java.util.List;

/** 5 分钟检索单元:LLM 摘要 + 关键词 + Embedding,rawSegments 保留原始证据。 */
public record VideoChunk(long startMs, long endMs, String summary, List<String> keywords,
                         List<VideoContextData.VideoSegment> rawSegments, List<Double> embedding) {

    public record ChunkSummary(String summary, List<String> keywords) {
    }
}
