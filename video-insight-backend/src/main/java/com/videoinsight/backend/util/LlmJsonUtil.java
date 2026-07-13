package com.videoinsight.backend.util;

/** 从 LLM 自由文本回复里抠出第一个完整 JSON 对象(容忍 markdown 围栏与前后废话)。 */
public final class LlmJsonUtil {

    private LlmJsonUtil() {}

    public static String extractJsonObject(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("LLM returned empty response");
        }
        String cleaned = response.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("LLM response contains no JSON object");
        }
        return cleaned.substring(start, end + 1);
    }
}
