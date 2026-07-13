package com.videoinsight.backend.util;

import java.util.List;
import java.util.Locale;

/** 混合检索打分(纯函数):余弦相似度 0.7 + 关键词命中率 0.3。 */
public final class RetrievalScoreUtil {

    private RetrievalScoreUtil() {}

    public static double cosine(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.isEmpty() || left.size() != right.size()) {
            return 0;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.size(); i++) {
            dot += left.get(i) * right.get(i);
            leftNorm += left.get(i) * left.get(i);
            rightNorm += right.get(i) * right.get(i);
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    public static double keywordScore(String goal, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return 0;
        }
        String normalizedGoal = normalize(goal);
        long matched = keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank()
                        && normalizedGoal.contains(normalize(keyword)))
                .count();
        return (double) matched / keywords.size();
    }

    public static double hybridScore(double cosineScore, double keywordScore) {
        return cosineScore * 0.7 + keywordScore * 0.3;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
