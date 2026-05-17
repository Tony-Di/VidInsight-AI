package com.videoinsight.backend.websocket;

public record VideoStatusPush(
        Long videoId,
        String videoStatus,
        String audioUrl,
        String step
) {}
