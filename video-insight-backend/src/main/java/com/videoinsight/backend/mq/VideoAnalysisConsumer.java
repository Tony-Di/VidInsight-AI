package com.videoinsight.backend.mq;

import com.videoinsight.backend.model.message.VideoAnalysisMessage;
import com.videoinsight.backend.service.VideoAnalysisJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoAnalysisConsumer {

    private final VideoAnalysisJobService videoAnalysisJobService;

    @RabbitListener(queues = "${app.rabbitmq.video-analysis-queue}")
    public void consume(VideoAnalysisMessage message) {
        if (message == null || message.getVideoId() == null) {
            log.warn("Received invalid video analysis message: {}", message);
            return;
        }

        videoAnalysisJobService.executeAnalysis(message.getVideoId());
        // 正常返回 → Spring AMQP 自动 ACK，消息从队列删除
        // 抛出异常 → requeue-rejected=false → 消息路由到 DLQ，不循环重投
    }

    /**
     * 死信队列消费者：记录无法处理的消息，等待人工介入或后续补偿。
     * 触发场景：DB 宕机导致 selectById / updateById 失败，消息无法正常处理。
     */
    @RabbitListener(queues = "${app.rabbitmq.dlq}")
    public void consumeDlq(VideoAnalysisMessage message) {
        Long videoId = message != null ? message.getVideoId() : null;
        log.error("Dead letter received — videoId={} 消息处理失败已进入死信队列，需人工检查。", videoId);
    }
}
