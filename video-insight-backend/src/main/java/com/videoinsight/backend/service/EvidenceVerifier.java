package com.videoinsight.backend.service;

import com.videoinsight.backend.model.agent.AgentAnswer;
import com.videoinsight.backend.model.agent.VideoContextData;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 证据反向核验:Executor 给出的每条 evidence,回到原始 ASR/OCR 文本里查证。
 * 查不到 = 模型幻觉,由 AgentLoop 强制 Critic 不通过并要求重新绑定证据。
 */
@Component
public class EvidenceVerifier {

    private static final double MIN_BIGRAM_COVERAGE = 0.5;

    public boolean supported(VideoContextData context, AgentAnswer.Evidence evidence) {
        if (context == null || evidence == null
                || evidence.content() == null || evidence.content().isBlank()) {
            return false;
        }
        String source = evidence.source() == null ? "" : evidence.source().toUpperCase(Locale.ROOT);
        if (!source.contains("ASR") && !source.contains("OCR")) {
            return false;
        }
        return context.segments().stream()
                .filter(segment -> evidence.timestampMs() >= segment.startMs()
                        && evidence.timestampMs() < segment.endMs())
                .map(segment -> sourceText(segment, source))
                .anyMatch(candidate -> textMatches(evidence.content(), candidate));
    }

    private String sourceText(VideoContextData.VideoSegment segment, String source) {
        if (source.contains("ASR") && source.contains("OCR")) {
            return segment.transcript() + " " + String.join(" ", segment.ocrTexts());
        }
        if (source.contains("ASR")) {
            return segment.transcript();
        }
        return String.join(" ", segment.ocrTexts());
    }

    private boolean textMatches(String evidence, String candidate) {
        String normalizedEvidence = normalize(evidence);
        String normalizedCandidate = normalize(candidate);
        if (normalizedEvidence.isEmpty() || normalizedCandidate.isEmpty()) {
            return false;
        }
        if (normalizedCandidate.contains(normalizedEvidence)
                || normalizedEvidence.contains(normalizedCandidate)) {
            return true;
        }
        if (normalizedEvidence.length() < 4 || normalizedCandidate.length() < 4) {
            return false;
        }
        Set<String> evidenceBigrams = bigrams(normalizedEvidence);
        Set<String> candidateBigrams = bigrams(normalizedCandidate);
        long overlap = evidenceBigrams.stream().filter(candidateBigrams::contains).count();
        return (double) overlap / evidenceBigrams.size() >= MIN_BIGRAM_COVERAGE;
    }

    private Set<String> bigrams(String value) {
        Set<String> result = new HashSet<>();
        for (int i = 0; i < value.length() - 1; i++) {
            result.add(value.substring(i, i + 2));
        }
        return result;
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}\\s]+", "");
    }
}
