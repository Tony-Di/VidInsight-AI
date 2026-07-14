package com.videoinsight.backend.mq;

import com.videoinsight.backend.config.RabbitMqProperties;
import com.videoinsight.backend.model.message.AgentAnalysisMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentAnalysisProducer {

    private final RabbitTemplate rabbitTemplate;

    private final RabbitMqProperties rabbitMqProperties;

    public void send(Long taskId) {
        rabbitTemplate.convertAndSend(
                rabbitMqProperties.getVideoAnalysisExchange(),
                rabbitMqProperties.getAgentRoutingKey(),
                new AgentAnalysisMessage(taskId));
    }
}
