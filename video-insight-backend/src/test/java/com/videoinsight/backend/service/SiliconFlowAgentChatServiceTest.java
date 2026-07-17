package com.videoinsight.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videoinsight.backend.config.SiliconFlowProperties;
import com.videoinsight.backend.model.agent.AgentPlan;
import com.videoinsight.backend.model.agent.VideoContextData;
import com.videoinsight.backend.service.impl.SiliconFlowAgentChatServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SiliconFlowAgentChatServiceTest {

    private static SiliconFlowProperties props() {
        SiliconFlowProperties p = new SiliconFlowProperties();
        p.setApiKey("test-key");
        p.setBaseUrl("http://localhost:1");
        p.setChatModel("test-model");
        return p;
    }

    /** 覆写 chat/sleep:不发真实 HTTP,按脚本吐响应或异常,并记录退避间隔。 */
    private static class ScriptedChat extends SiliconFlowAgentChatServiceImpl {
        private final Deque<Object> script;
        final List<Long> sleeps = new ArrayList<>();

        ScriptedChat(Object... steps) {
            super(props(), new ObjectMapper());
            this.script = new ArrayDeque<>(List.of(steps));
        }

        @Override
        protected String chat(String prompt) {
            Object next = script.pop();
            if (next instanceof RuntimeException e) {
                throw e;
            }
            return (String) next;
        }

        @Override
        protected void sleep(long millis) {
            sleeps.add(millis);
        }
    }

    private static VideoContextData context() {
        return new VideoContextData("src", List.of(
                new VideoContextData.VideoSegment(0, 60_000, "hello", List.of(), List.of())));
    }

    @Test
    void retriesWithExponentialBackoffThenSucceeds() {
        ScriptedChat chat = new ScriptedChat(
                new IllegalStateException("http 500"),
                new IllegalStateException("http 500"),
                "{\"understoodGoal\":\"g\",\"tasks\":[\"t1\",\"t2\",\"t3\"]}");

        AgentPlan plan = chat.plan("总结视频重点", context());

        assertEquals("g", plan.understoodGoal());
        assertEquals(List.of(1000L, 2000L), chat.sleeps, "第 1/2 次失败后应分别退避 1s、2s");
    }

    @Test
    void malformedJsonAlsoRetriedThenFails() {
        ScriptedChat chat = new ScriptedChat("不是 JSON", "还不是", "依旧不是");

        assertThrows(IllegalStateException.class, () -> chat.plan("总结视频重点", context()));
        assertEquals(List.of(1000L, 2000L), chat.sleeps, "坏 JSON 同样触发重试");
    }
}
