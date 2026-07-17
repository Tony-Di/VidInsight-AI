package com.videoinsight.backend.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmJsonUtilTest {

    @Test
    void stripsMarkdownFence() {
        assertEquals("{\"a\":1}", LlmJsonUtil.extractJsonObject("```json\n{\"a\":1}\n```"));
    }

    @Test
    void extractsObjectSurroundedByProse() {
        assertEquals("{\"a\":1}", LlmJsonUtil.extractJsonObject("好的,结果如下:{\"a\":1} 请查收"));
    }

    @Test
    void throwsWhenNoJsonObject() {
        assertThrows(IllegalStateException.class, () -> LlmJsonUtil.extractJsonObject("抱歉我做不到"));
    }

    @Test
    void throwsOnEmptyResponse() {
        assertThrows(IllegalStateException.class, () -> LlmJsonUtil.extractJsonObject("  "));
    }
}
