package com.videoinsight.backend.util;

import com.videoinsight.backend.model.agent.VideoContextData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 把 ASR 分段与 OCR 关键帧按 60 秒窗口合并成 VideoSegment(纯函数,便于单测)。 */
public final class VideoSegmentMerger {

    public static final long WINDOW_MS = 60_000L;

    private VideoSegmentMerger() {}

    public record TranscriptPart(long startMs, long endMs, String text) {}

    public record FramePart(long timestampMs, String ocrText, String frameRef) {}

    public static List<VideoContextData.VideoSegment> merge(List<TranscriptPart> transcripts,
                                                            List<FramePart> frames) {
        Map<Long, Builder> windows = new TreeMap<>();
        for (TranscriptPart part : transcripts) {
            windows.computeIfAbsent(windowStart(part.startMs()), Builder::new)
                    .transcripts.add(part.text());
        }
        for (FramePart frame : frames) {
            Builder builder = windows.computeIfAbsent(windowStart(frame.timestampMs()), Builder::new);
            if (frame.ocrText() != null && !frame.ocrText().isBlank()) {
                builder.ocrTexts.add(frame.ocrText());
            }
            builder.evidenceFrames.add(frame.frameRef());
        }
        return windows.values().stream().map(Builder::build).toList();
    }

    private static long windowStart(long timestampMs) {
        return timestampMs / WINDOW_MS * WINDOW_MS;
    }

    private static final class Builder {
        private final long startMs;
        private final List<String> transcripts = new ArrayList<>();
        private final List<String> ocrTexts = new ArrayList<>();
        private final List<String> evidenceFrames = new ArrayList<>();

        private Builder(long startMs) {
            this.startMs = startMs;
        }

        private VideoContextData.VideoSegment build() {
            return new VideoContextData.VideoSegment(startMs, startMs + WINDOW_MS,
                    String.join("\n", transcripts), List.copyOf(ocrTexts), List.copyOf(evidenceFrames));
        }
    }
}
