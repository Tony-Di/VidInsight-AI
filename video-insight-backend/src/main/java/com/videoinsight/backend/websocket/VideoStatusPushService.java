package com.videoinsight.backend.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoStatusPushService {

    private final SimpMessagingTemplate messagingTemplate;

    public void push(Long userId, VideoStatusPush push) {
        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/video-status",
                    push
            );
        } catch (Exception e) {
            log.warn("WebSocket push failed for userId={}, videoId={}: {}",
                    userId, push.videoId(), e.getMessage());
        }
    }
}
