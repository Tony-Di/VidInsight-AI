package com.videoinsight.backend.service;

import com.videoinsight.backend.model.agent.AgentAnswer;
import com.videoinsight.backend.model.agent.VideoContextData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceVerifierTest {

    private final EvidenceVerifier verifier = new EvidenceVerifier();

    private VideoContextData context() {
        return new VideoContextData("src", List.of(
                new VideoContextData.VideoSegment(0, 60_000,
                        "接下来讲解二叉树的前序遍历,根左右的顺序",
                        List.of("前序遍历:根节点、左子树、右子树"), List.of("30000")),
                new VideoContextData.VideoSegment(60_000, 120_000,
                        "然后是中序遍历", List.of(), List.of())));
    }

    @Test
    void exactSubstringInAsrIsSupported() {
        assertTrue(verifier.supported(context(),
                new AgentAnswer.Evidence(10_000, "ASR", "二叉树的前序遍历")));
    }

    @Test
    void ocrSourceChecksOcrTexts() {
        assertTrue(verifier.supported(context(),
                new AgentAnswer.Evidence(30_000, "OCR", "前序遍历:根节点、左子树、右子树")));
    }

    @Test
    void timestampOutsideAllSegmentsIsRejected() {
        assertFalse(verifier.supported(context(),
                new AgentAnswer.Evidence(999_000, "ASR", "二叉树的前序遍历")));
    }

    @Test
    void fabricatedContentIsRejected() {
        assertFalse(verifier.supported(context(),
                new AgentAnswer.Evidence(10_000, "ASR", "本视频介绍了快速排序的时间复杂度")));
    }

    @Test
    void unknownSourceIsRejected() {
        assertFalse(verifier.supported(context(),
                new AgentAnswer.Evidence(10_000, "narration", "二叉树的前序遍历")));
    }

    @Test
    void paraphraseWithHighBigramOverlapIsSupported() {
        assertTrue(verifier.supported(context(),
                new AgentAnswer.Evidence(10_000, "ASR", "讲解二叉树的前序遍历,根左右顺序")));
    }
}
