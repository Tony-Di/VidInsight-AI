package com.videoinsight.backend.websocket;

import com.videoinsight.backend.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }
        String raw = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(raw) || !raw.startsWith("Bearer ")) {
            throw new MessageDeliveryException(message, "missing or malformed Authorization header");
        }
        String token = raw.substring(7);
        try {
            Claims claims = jwtUtil.parse(token);
            Long userId = jwtUtil.extractUserId(claims);
            Principal principal = userId::toString;
            accessor.setUser(new UsernamePasswordAuthenticationToken(principal, null, null));
        } catch (JwtException e) {
            log.warn("WebSocket CONNECT rejected: {}", e.getMessage());
            throw new MessageDeliveryException(message, "invalid JWT: " + e.getMessage());
        }
        return message;
    }
}
