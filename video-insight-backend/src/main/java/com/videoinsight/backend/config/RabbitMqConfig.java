package com.videoinsight.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;

@Configuration
@RequiredArgsConstructor
public class RabbitMqConfig {

    private final RabbitMqProperties rabbitMqProperties;

    // ── 主队列 ──────────────────────────────────────────────────

    @Bean
    public DirectExchange videoAnalysisExchange() {
        return new DirectExchange(rabbitMqProperties.getVideoAnalysisExchange(), true, false);
    }

    /**
     * 主队列声明时绑定 DLX，消息被 reject 且不重投时自动路由到死信队列。
     * 注意：若 RabbitMQ 中已存在同名队列（无 DLX 参数），需先在管理界面手动删除旧队列再重启。
     */
    @Bean
    public Queue videoAnalysisQueue() {
        return QueueBuilder.durable(rabbitMqProperties.getVideoAnalysisQueue())
                .withArgument("x-dead-letter-exchange", rabbitMqProperties.getDlx())
                .withArgument("x-dead-letter-routing-key", rabbitMqProperties.getDlqRoutingKey())
                .build();
    }

    @Bean
    public Binding videoAnalysisBinding(Queue videoAnalysisQueue, DirectExchange videoAnalysisExchange) {
        return BindingBuilder.bind(videoAnalysisQueue)
                .to(videoAnalysisExchange)
                .with(rabbitMqProperties.getVideoAnalysisRoutingKey());
    }

    // ── 死信队列（DLQ） ─────────────────────────────────────────

    @Bean
    public DirectExchange videoAnalysisDlx() {
        return new DirectExchange(rabbitMqProperties.getDlx(), true, false);
    }

    @Bean
    public Queue videoAnalysisDlq() {
        return QueueBuilder.durable(rabbitMqProperties.getDlq()).build();
    }

    @Bean
    public Binding videoAnalysisDlqBinding(Queue videoAnalysisDlq, DirectExchange videoAnalysisDlx) {
        return BindingBuilder.bind(videoAnalysisDlq)
                .to(videoAnalysisDlx)
                .with(rabbitMqProperties.getDlqRoutingKey());
    }

    // ── 公共配置 ────────────────────────────────────────────────

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        // 消费者抛异常时不重新入队，直接路由到 DLQ
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
