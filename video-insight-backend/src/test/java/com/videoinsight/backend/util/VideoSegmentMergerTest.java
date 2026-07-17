package com.videoinsight.backend.util;

import com.videoinsight.backend.model.agent.VideoContextData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoSegmentMergerTest {

    @Test
    void mergesTranscriptAndFrameIntoSameWindow() {
        List<VideoContextData.VideoSegment> segments = VideoSegmentMerger.merge(
                List.of(new VideoSegmentMerger.TranscriptPart(0, 60_000, "第一分钟的语音")),
                List.of(new VideoSegmentMerger.FramePart(30_000, "板书文字", "30000")));

        assertEquals(1, segments.size());
        VideoContextData.VideoSegment segment = segments.get(0);
        assertEquals(0, segment.startMs());
        assertEquals(60_000, segment.endMs());
        assertEquals("第一分钟的语音", segment.transcript());
        assertEquals(List.of("板书文字"), segment.ocrTexts());
        assertEquals(List.of("30000"), segment.evidenceFrames());
    }

    @Test
    void frameInLaterWindowCreatesSecondSegmentSortedByStart() {
        List<VideoContextData.VideoSegment> segments = VideoSegmentMerger.merge(
                List.of(new VideoSegmentMerger.TranscriptPart(0, 60_000, "a")),
                List.of(new VideoSegmentMerger.FramePart(70_000, "b", "70000")));

        assertEquals(2, segments.size());
        assertEquals(0, segments.get(0).startMs());
        assertEquals(60_000, segments.get(1).startMs());
    }

    @Test
    void blankOcrTextIsDroppedButFrameRefKeptAsEvidence() {
        List<VideoContextData.VideoSegment> segments = VideoSegmentMerger.merge(
                List.of(),
                List.of(new VideoSegmentMerger.FramePart(5_000, "   ", "5000")));

        assertEquals(1, segments.size());
        assertTrue(segments.get(0).ocrTexts().isEmpty());
        assertEquals(List.of("5000"), segments.get(0).evidenceFrames());
    }
}
