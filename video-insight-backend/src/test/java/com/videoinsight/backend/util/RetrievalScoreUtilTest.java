package com.videoinsight.backend.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetrievalScoreUtilTest {

    @Test
    void cosineOfIdenticalVectorsIsOne() {
        assertEquals(1.0, RetrievalScoreUtil.cosine(List.of(0.6, 0.8), List.of(0.6, 0.8)), 1e-9);
    }

    @Test
    void cosineOfOrthogonalVectorsIsZero() {
        assertEquals(0.0, RetrievalScoreUtil.cosine(List.of(1.0, 0.0), List.of(0.0, 1.0)), 1e-9);
    }

    @Test
    void cosineOfMismatchedOrEmptyVectorsIsZero() {
        assertEquals(0.0, RetrievalScoreUtil.cosine(List.of(1.0), List.of(1.0, 2.0)), 1e-9);
        assertEquals(0.0, RetrievalScoreUtil.cosine(List.of(), List.of()), 1e-9);
    }

    @Test
    void keywordScoreIsMatchedFraction() {
        double score = RetrievalScoreUtil.keywordScore("讲讲二叉树的前序遍历",
                List.of("二叉树", "快速排序"));
        assertEquals(0.5, score, 1e-9);
    }

    @Test
    void hybridScoreWeights70Cosine30Keyword() {
        assertEquals(0.79, RetrievalScoreUtil.hybridScore(0.7, 1.0), 1e-9);
    }
}
