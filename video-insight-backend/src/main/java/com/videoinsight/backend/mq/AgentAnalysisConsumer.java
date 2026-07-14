package com.videoinsight.backend.mq;

import com.videoinsight.backend.model.message.AgentAnalysisMessage;
import com.videoinsight.backend.service.AgentAnalysisJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentAnalysisConsumer {

    private final AgentAnalysisJobService agentAnalysisJobService;

    @RabbitListener(queues = "${app.rabbitmq.agent-queue}")
    public void consume(AgentAnalysisMessage message) {
        if (message == null || message.getTaskId() == null) {
            log.warn("Received invalid agent analysis message: {}", message);
            return;
        }
        agentAnalysisJobService.execute(message.getTaskId());
        // 正常返回 → ACK;抛异常(仅基础设施故障)→ requeue-rejected=false → 进 agent DLQ
    }

    /** Agent 死信兜底:任务标 FAILED,前端轮询能看到终态而不是永远 PROCESSING。 */
    @RabbitListener(queues = "${app.rabbitmq.agent-dlq}")
    public void consumeDlq(AgentAnalysisMessage message) {
        Long taskId = message != null ? message.getTaskId() : null;
        log.error("Agent dead letter received — taskId={}", taskId);
        if (taskId != null) {
            agentAnalysisJobService.markDeadLetter(taskId);
        }
    }
}
