package com.videoinsight.backend.model.agent;

import java.util.List;

/** 与分析目标无关的多模态视频上下文,每视频构建一次后 JSON 持久化到 video_agent_context。 */
public record VideoContextData(String source, List<VideoSegment> segments) {

    /** 60 秒窗口:窗口内语音转写 + 画面 OCR 文本 + 证据帧时间戳(ms 字符串)。 */
    public record VideoSegment(long startMs, long endMs, String transcript,
                               List<String> ocrTexts, List<String> evidenceFrames) {
    }
}
