package com.videoinsight.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.rabbitmq")
public class RabbitMqProperties {

    private String videoAnalysisExchange;

    private String videoAnalysisQueue;

    private String videoAnalysisRoutingKey;

    private String dlx;

    private String dlq;

    private String dlqRoutingKey;

    private String agentQueue;

    private String agentRoutingKey;

    private String agentDlq;

    private String agentDlqRoutingKey;
}
