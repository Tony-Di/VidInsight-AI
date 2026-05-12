package com.videoinsight.backend.mq;

import com.videoinsight.backend.config.RabbitMqProperties;
import com.videoinsight.backend.model.message.VideoAnalysisMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoAnalysisProducer {

    private final RabbitTemplate rabbitTemplate;

    private final RabbitMqProperties rabbitMqProperties;

    public void send(Long videoId) {
        rabbitTemplate.convertAndSend(
                rabbitMqProperties.getVideoAnalysisExchange(),
                rabbitMqProperties.getVideoAnalysisRoutingKey(),
                new VideoAnalysisMessage(videoId)
        );
    }
}
