package com.videoinsight.backend.model.agent;

import java.util.List;

/** Executor 产物:每条重要结论必须能对应 evidence 里的时间戳证据。 */
public record AgentAnswer(String title, List<String> conclusions,
                          List<Evidence> evidence, List<String> suggestions) {

    /** source 只允许 "ASR" 或 "OCR";timestampMs 必须落在某个 VideoSegment 区间内。 */
    public record Evidence(long timestampMs, String source, String content) {
    }
}
