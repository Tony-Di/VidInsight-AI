# WebSocket STOMP 实时推送 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 STOMP over WebSocket 替代前端 2.4s 轮询，视频状态变化时后端主动推送，前端即时更新。

**Architecture:** 后端注册 STOMP endpoint `/ws`，ChannelInterceptor 验证 JWT 设置 principal，状态写库后调 `VideoStatusPushService.push()` → `convertAndSendToUser(userId, "/queue/video-status", push)`。前端用 `@stomp/stompjs` 订阅 `/user/queue/video-status`，收到消息更新 videos state，删除现有轮询 useEffect。

**Tech Stack:** spring-boot-starter-websocket, @stomp/stompjs, JUnit 5 + Mockito

---

## 文件清单

### 新增
| 文件 | 职责 |
|------|------|
| `video-insight-backend/src/main/java/com/videoinsight/backend/config/WebSocketConfig.java` | 注册 STOMP endpoint，配置 broker，挂载 interceptor |
| `video-insight-backend/src/main/java/com/videoinsight/backend/websocket/WebSocketAuthInterceptor.java` | ChannelInterceptor：CONNECT 帧验 JWT，设 principal |
| `video-insight-backend/src/main/java/com/videoinsight/backend/websocket/VideoStatusPush.java` | Push DTO record |
| `video-insight-backend/src/main/java/com/videoinsight/backend/websocket/VideoStatusPushService.java` | 封装 SimpMessagingTemplate，fail-silent |
| `video-insight-backend/src/test/java/com/videoinsight/backend/websocket/VideoStatusPushServiceTest.java` | 单元测试 |
| `video-insight-backend/src/test/java/com/videoinsight/backend/websocket/WebSocketAuthInterceptorTest.java` | 单元测试 |

### 修改
| 文件 | 改动 |
|------|------|
| `video-insight-backend/pom.xml` | 加 `spring-boot-starter-websocket` |
| `video-insight-backend/src/main/java/com/videoinsight/backend/config/SecurityConfig.java` | `/ws/**` 加入 permitAll |
| `video-insight-backend/src/main/java/com/videoinsight/backend/service/impl/VideoAnalysisJobServiceImpl.java` | 注入 pushService，COMPLETED/FAILED 后 push |
| `video-insight-backend/src/main/java/com/videoinsight/backend/service/impl/VideoImportTaskServiceImpl.java` | 注入 pushService，PENDING/IMPORT_FAILED/COMPLETED-复用 后 push |
| `video-insight-frontend/package.json` | 加 `@stomp/stompjs` |
| `video-insight-frontend/src/App.tsx` | 删轮询，加 STOMP 连接逻辑 |

---

## Task 1: 依赖 + DTO

**Files:**
- Modify: `video-insight-backend/pom.xml`
- Create: `video-insight-backend/src/main/java/com/videoinsight/backend/websocket/VideoStatusPush.java`

- [ ] **Step 1: 在 pom.xml 加 websocket 依赖**

在 `<dependencies>` 末尾（`spring-boot-starter-test` 之前）加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

- [ ] **Step 2: 创建 VideoStatusPush record**

新建 `video-insight-backend/src/main/java/com/videoinsight/backend/websocket/VideoStatusPush.java`：

```java
package com.videoinsight.backend.websocket;

public record VideoStatusPush(
        Long videoId,
        String videoStatus,
        String audioUrl
) {}
```

- [ ] **Step 3: 编译验证**

```bash
cd video-insight-backend
.\mvnw.cmd compile -q
```

预期：无报错输出。

- [ ] **Step 4: 提交**

```bash
git add video-insight-backend/pom.xml
git add "video-insight-backend/src/main/java/com/videoinsight/backend/websocket/VideoStatusPush.java"
git commit -m "feat(ws): add websocket dependency and VideoStatusPush DTO"
```

---

## Task 2: VideoStatusPushService（TDD）

**Files:**
- Create: `video-insight-backend/src/main/java/com/videoinsight/backend/websocket/VideoStatusPushService.java`
- Create: `video-insight-backend/src/test/java/com/videoinsight/backend/websocket/VideoStatusPushServiceTest.java`

- [ ] **Step 1: 写失败测试**

新建 `video-insight-backend/src/test/java/com/videoinsight/backend/websocket/VideoStatusPushServiceTest.java`：

```java
package com.videoinsight.backend.websocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoStatusPushServiceTest {

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    VideoStatusPushService pushService;

    @Test
    void push_sends_to_correct_user_and_destination() {
        var push = new VideoStatusPush(42L, "COMPLETED", "/uploads/audio.mp3");

        pushService.push(7L, push);

        verify(messagingTemplate).convertAndSendToUser(
                eq("7"),
                eq("/queue/video-status"),
                eq(push)
        );
    }

    @Test
    void push_is_silent_when_messaging_throws() {
        doThrow(new RuntimeException("broker down"))
                .when(messagingTemplate).convertAndSendToUser(any(), any(), any());
        var push = new VideoStatusPush(1L, "FAILED", null);

        assertDoesNotThrow(() -> pushService.push(99L, push));
    }
}
```

- [ ] **Step 2: 运行，确认编译失败（类不存在）**

```bash
cd video-insight-backend
.\mvnw.cmd test -Dtest=VideoStatusPushServiceTest -q 2>&1 | head -20
```

预期：`COMPILATION ERROR` 或 `cannot find symbol: class VideoStatusPushService`。

- [ ] **Step 3: 实现 VideoStatusPushService**

新建 `video-insight-backend/src/main/java/com/videoinsight/backend/websocket/VideoStatusPushService.java`：

```java
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
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
cd video-insight-backend
.\mvnw.cmd test -Dtest=VideoStatusPushServiceTest -q
```

预期：`BUILD SUCCESS`，2 tests passed。

- [ ] **Step 5: 提交**

```bash
git add "video-insight-backend/src/main/java/com/videoinsight/backend/websocket/VideoStatusPushService.java"
git add "video-insight-backend/src/test/java/com/videoinsight/backend/websocket/VideoStatusPushServiceTest.java"
git commit -m "feat(ws): add VideoStatusPushService with unit tests"
```

---

## Task 3: WebSocketAuthInterceptor（TDD）

**Files:**
- Create: `video-insight-backend/src/main/java/com/videoinsight/backend/websocket/WebSocketAuthInterceptor.java`
- Create: `video-insight-backend/src/test/java/com/videoinsight/backend/websocket/WebSocketAuthInterceptorTest.java`

- [ ] **Step 1: 写失败测试**

新建 `video-insight-backend/src/test/java/com/videoinsight/backend/websocket/WebSocketAuthInterceptorTest.java`：

```java
package com.videoinsight.backend.websocket;

import com.videoinsight.backend.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock
    JwtUtil jwtUtil;

    @Mock
    MessageChannel channel;

    @InjectMocks
    WebSocketAuthInterceptor interceptor;

    @Test
    void connect_with_valid_jwt_sets_principal_name_to_user_id() {
        Claims claims = mock(Claims.class);
        when(jwtUtil.parse("valid-token")).thenReturn(claims);
        when(jwtUtil.extractUserId(claims)).thenReturn(42L);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer valid-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("42");
    }

    @Test
    void connect_with_invalid_jwt_throws_MessageDeliveryException() {
        when(jwtUtil.parse("bad-token")).thenThrow(new JwtException("invalid"));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer bad-token");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void connect_with_missing_header_throws_MessageDeliveryException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void non_connect_frames_pass_through_unchanged() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isEqualTo(message);
        verifyNoInteractions(jwtUtil);
    }
}
```

- [ ] **Step 2: 运行，确认编译失败**

```bash
cd video-insight-backend
.\mvnw.cmd test -Dtest=WebSocketAuthInterceptorTest -q 2>&1 | head -20
```

预期：`cannot find symbol: class WebSocketAuthInterceptor`。

- [ ] **Step 3: 实现 WebSocketAuthInterceptor**

新建 `video-insight-backend/src/main/java/com/videoinsight/backend/websocket/WebSocketAuthInterceptor.java`：

```java
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
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

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
```

- [ ] **Step 4: 运行测试，确认通过**

```bash
cd video-insight-backend
.\mvnw.cmd test -Dtest=WebSocketAuthInterceptorTest -q
```

预期：`BUILD SUCCESS`，4 tests passed。

- [ ] **Step 5: 提交**

```bash
git add "video-insight-backend/src/main/java/com/videoinsight/backend/websocket/WebSocketAuthInterceptor.java"
git add "video-insight-backend/src/test/java/com/videoinsight/backend/websocket/WebSocketAuthInterceptorTest.java"
git commit -m "feat(ws): add WebSocketAuthInterceptor with JWT validation tests"
```

---

## Task 4: WebSocketConfig + SecurityConfig

**Files:**
- Create: `video-insight-backend/src/main/java/com/videoinsight/backend/config/WebSocketConfig.java`
- Modify: `video-insight-backend/src/main/java/com/videoinsight/backend/config/SecurityConfig.java`

- [ ] **Step 1: 创建 WebSocketConfig**

新建 `video-insight-backend/src/main/java/com/videoinsight/backend/config/WebSocketConfig.java`：

```java
package com.videoinsight.backend.config;

import com.videoinsight.backend.websocket.WebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
    }
}
```

- [ ] **Step 2: 修改 SecurityConfig，放行 /ws/\*\***

在 `SecurityConfig.java` 的 `permitAll` 列表中，在 `"/uploads/**"` 后面加 `"/ws/**"`：

```java
.requestMatchers(
        "/api/auth/register",
        "/api/auth/login",
        "/api/health",
        "/api/health/**",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/uploads/**",
        "/ws/**"          // ← 新增这一行
).permitAll()
```

- [ ] **Step 3: 编译验证**

```bash
cd video-insight-backend
.\mvnw.cmd compile -q
```

预期：无报错。

- [ ] **Step 4: 提交**

```bash
git add "video-insight-backend/src/main/java/com/videoinsight/backend/config/WebSocketConfig.java"
git add "video-insight-backend/src/main/java/com/videoinsight/backend/config/SecurityConfig.java"
git commit -m "feat(ws): add WebSocketConfig and permit /ws/** in SecurityConfig"
```

---

## Task 5: 注入 push 到 VideoAnalysisJobServiceImpl

**Files:**
- Modify: `video-insight-backend/src/main/java/com/videoinsight/backend/service/impl/VideoAnalysisJobServiceImpl.java`

- [ ] **Step 1: 修改 VideoAnalysisJobServiceImpl**

在文件顶部加 import：
```java
import com.videoinsight.backend.websocket.VideoStatusPush;
import com.videoinsight.backend.websocket.VideoStatusPushService;
```

在 `private final VideoCacheService videoCacheService;` 后加字段：
```java
private final VideoStatusPushService videoStatusPushService;
```

在 COMPLETED 分支（`videoCacheService.evictUserLists(videoInfo.getUserId());` 之后）加：
```java
videoStatusPushService.push(videoInfo.getUserId(),
        new VideoStatusPush(videoId, "COMPLETED", videoInfo.getAudioUrl()));
```

在 FAILED 分支（catch 块中 `videoCacheService.evictUserLists(videoInfo.getUserId());` 之后）加：
```java
videoStatusPushService.push(videoInfo.getUserId(),
        new VideoStatusPush(videoId, "FAILED", null));
```

完整修改后的 `executeAnalysis` 方法：

```java
@Override
public void executeAnalysis(Long videoId) {
    VideoInfo videoInfo = videoInfoMapper.selectById(videoId);
    if (videoInfo == null) {
        log.warn("Video analysis skipped because video {} does not exist.", videoId);
        return;
    }

    if (videoInfo.getVideoStatus() == VideoStatus.COMPLETED) {
        log.info("Video {} already completed, skipping duplicate MQ delivery.", videoId);
        return;
    }

    try {
        VideoAnalysisResult result = videoAnalysisService.analyze(videoInfo);
        videoInfo.setVideoStatus(VideoStatus.COMPLETED);
        videoInfo.setAudioUrl(result.getAudioUrl());
        videoInfo.setTranscript(result.getTranscript());
        videoInfo.setSummary(result.getSummary());
        videoInfo.setUpdatedAt(LocalDateTime.now());
        videoInfoMapper.updateById(videoInfo);
        videoCacheService.evictDetail(videoId);
        videoCacheService.evictUserLists(videoInfo.getUserId());
        videoStatusPushService.push(videoInfo.getUserId(),
                new VideoStatusPush(videoId, "COMPLETED", videoInfo.getAudioUrl()));
    } catch (Exception exception) {
        log.error("Video analysis failed, videoId={}", videoId, exception);
        videoInfo.setVideoStatus(VideoStatus.FAILED);
        videoInfo.setSummary(getRootCauseMessage(exception));
        videoInfo.setUpdatedAt(LocalDateTime.now());
        videoInfoMapper.updateById(videoInfo);
        videoCacheService.evictDetail(videoId);
        videoCacheService.evictUserLists(videoInfo.getUserId());
        videoStatusPushService.push(videoInfo.getUserId(),
                new VideoStatusPush(videoId, "FAILED", null));
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd video-insight-backend
.\mvnw.cmd compile -q
```

预期：无报错。

- [ ] **Step 3: 提交**

```bash
git add "video-insight-backend/src/main/java/com/videoinsight/backend/service/impl/VideoAnalysisJobServiceImpl.java"
git commit -m "feat(ws): push video status on analysis COMPLETED/FAILED"
```

---

## Task 6: 注入 push 到 VideoImportTaskServiceImpl

**Files:**
- Modify: `video-insight-backend/src/main/java/com/videoinsight/backend/service/impl/VideoImportTaskServiceImpl.java`

- [ ] **Step 1: 修改 VideoImportTaskServiceImpl**

在文件顶部加 import：
```java
import com.videoinsight.backend.websocket.VideoStatusPush;
import com.videoinsight.backend.websocket.VideoStatusPushService;
```

在 `private final RedissonClient redissonClient;` 后加字段：
```java
private final VideoStatusPushService videoStatusPushService;
```

**在 `submitImport` 的 try 块成功路径**，`videoCacheService.evictUserLists(videoInfo.getUserId());` 之后加：
```java
videoStatusPushService.push(videoInfo.getUserId(),
        new VideoStatusPush(videoInfo.getId(), "PENDING", null));
```

**在 catch 块**，`videoCacheService.evictUserLists(videoInfo.getUserId());` 之后加：
```java
videoStatusPushService.push(videoInfo.getUserId(),
        new VideoStatusPush(videoInfo.getId(), "IMPORT_FAILED", null));
```

**在 `submitImport` 中 MD5 复用命中之后**（`if (md5 != null && reuseIfDuplicate(...))` 的 return 之前）：
```java
if (md5 != null && reuseIfDuplicate(videoInfo, md5)) {
    videoStatusPushService.push(videoInfo.getUserId(),
            new VideoStatusPush(videoInfo.getId(), "COMPLETED", videoInfo.getAudioUrl()));
    return;
}
```

完整修改后的 `submitImport` 方法：

```java
@Async("analysisTaskExecutor")
@Override
public void submitImport(Long videoId, String sourceUrl) {
    VideoInfo videoInfo = videoInfoMapper.selectById(videoId);
    if (videoInfo == null) {
        log.warn("Video import skipped because video {} does not exist.", videoId);
        return;
    }

    Path downloadedFile = null;
    try {
        downloadedFile = videoDownloadService.download(sourceUrl);

        String md5 = computeMd5OrNull(downloadedFile);
        if (md5 != null && reuseIfDuplicate(videoInfo, md5)) {
            videoStatusPushService.push(videoInfo.getUserId(),
                    new VideoStatusPush(videoInfo.getId(), "COMPLETED", videoInfo.getAudioUrl()));
            return;
        }

        String localSourceUrl = fileStorageService.saveVideo(downloadedFile, downloadedFile.getFileName().toString());

        videoInfo.setSourceUrl(localSourceUrl);
        videoInfo.setFileMd5(md5);
        videoInfo.setVideoStatus(VideoStatus.PENDING);
        videoInfo.setSummary(null);
        videoInfo.setUpdatedAt(LocalDateTime.now());
        videoInfoMapper.updateById(videoInfo);
        videoCacheService.evictDetail(videoInfo.getId());
        videoCacheService.evictUserLists(videoInfo.getUserId());
        videoStatusPushService.push(videoInfo.getUserId(),
                new VideoStatusPush(videoInfo.getId(), "PENDING", null));
    } catch (Exception exception) {
        log.error("Video import failed, videoId={}", videoId, exception);
        videoInfo.setVideoStatus(VideoStatus.IMPORT_FAILED);
        videoInfo.setSummary(getRootCauseMessage(exception));
        videoInfo.setUpdatedAt(LocalDateTime.now());
        videoInfoMapper.updateById(videoInfo);
        videoCacheService.evictDetail(videoInfo.getId());
        videoCacheService.evictUserLists(videoInfo.getUserId());
        videoStatusPushService.push(videoInfo.getUserId(),
                new VideoStatusPush(videoInfo.getId(), "IMPORT_FAILED", null));
    } finally {
        deleteTempFile(downloadedFile);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd video-insight-backend
.\mvnw.cmd compile -q
```

预期：无报错。

- [ ] **Step 3: 运行全部单元测试**

```bash
cd video-insight-backend
.\mvnw.cmd test -q
```

预期：`BUILD SUCCESS`，所有 tests passed。

- [ ] **Step 4: 提交**

```bash
git add "video-insight-backend/src/main/java/com/videoinsight/backend/service/impl/VideoImportTaskServiceImpl.java"
git commit -m "feat(ws): push video status on import PENDING/IMPORT_FAILED/COMPLETED-reuse"
```

---

## Task 7: 前端——安装依赖，删轮询，加 STOMP

**Files:**
- Modify: `video-insight-frontend/package.json`
- Modify: `video-insight-frontend/src/App.tsx`

- [ ] **Step 1: 安装 @stomp/stompjs**

```bash
cd video-insight-frontend
npm install @stomp/stompjs
```

预期：`package.json` 中出现 `"@stomp/stompjs": "^X.X.X"`。

- [ ] **Step 2: 在 App.tsx 顶部加 import**

在现有 import 块末尾加：

```typescript
import { Client } from '@stomp/stompjs';
```

- [ ] **Step 3: 在 AppInner 中声明 VideoStatusPush 类型**

在 `AppInner` 函数定义之前加（和其他 type 定义放在一起）：

```typescript
interface VideoStatusPush {
  videoId: number;
  videoStatus: VideoStatus;
  audioUrl: string | null;
}
```

- [ ] **Step 4: 删除现有轮询 useEffect**

找到并完整删除下面这段 useEffect（约 30 行，包含 `pollTimerRef`）：

```typescript
useEffect(() => {
  if (pollTimerRef.current) {
    window.clearInterval(pollTimerRef.current);
    pollTimerRef.current = null;
  }
  if (!activeVideo || !POLLING_STATUSES.has(activeVideo.videoStatus)) return;

  pollTimerRef.current = window.setInterval(async () => {
    // ... 整段删除
  }, 2400);

  return () => {
    if (pollTimerRef.current) window.clearInterval(pollTimerRef.current);
  };
}, [activeVideo]);
```

同时删除 `const pollTimerRef = useRef<number | null>(null);` 这一行。

- [ ] **Step 5: 加 STOMP useEffect**

在删掉的轮询 useEffect 原位置，加入下面这段：

```typescript
useEffect(() => {
  const token = getStoredToken();
  if (!token) return;

  const client = new Client({
    brokerURL: `ws://${window.location.hostname}:8080/ws`,
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 5000,
    onConnect: () => {
      client.subscribe('/user/queue/video-status', (frame) => {
        const push: VideoStatusPush = JSON.parse(frame.body);
        setVideos((curr) => {
          const prev = curr.find((v) => v.id === push.videoId);
          // setState 回调必须是纯函数，副作用用 setTimeout 推到下一个 tick
          if (prev?.videoStatus === 'IMPORTING' && push.videoStatus === 'PENDING') {
            setTimeout(() => void handleStartAnalysis({ id: push.videoId } as VideoInfo), 0);
          }
          return curr.map((v) => {
            if (v.id !== push.videoId) return v;
            return {
              ...v,
              videoStatus: push.videoStatus,
              ...(push.audioUrl !== null && { audioUrl: push.audioUrl }),
            };
          });
        });
      });
    },
    onStompError: (frame) => {
      console.warn('STOMP error', frame.headers['message']);
    },
  });

  client.activate();
  return () => { void client.deactivate(); };
}, [user.id]);
```

- [ ] **Step 6: 启动后端和前端，手动验证**

启动后端（需要 MySQL + Redis + RabbitMQ 运行）：
```bash
cd video-insight-backend
.\mvnw.cmd spring-boot:run
```

启动前端：
```bash
cd video-insight-frontend
npm run dev
```

验证步骤：
1. 打开浏览器，登录
2. 打开浏览器开发者工具 → Network → WS 标签
3. 确认 `ws://localhost:8080/ws` 连接建立，看到 STOMP CONNECTED 帧
4. 提交一个视频 URL 导入
5. 观察卡片状态：不刷新页面，状态应该从 IMPORTING → PENDING → PROCESSING → COMPLETED 自动更新
6. 确认没有 2.4s 间隔的 `/api/videos/{id}` 轮询请求（Network → XHR 标签）

- [ ] **Step 7: 提交**

```bash
cd video-insight-frontend
git add package.json package-lock.json src/App.tsx
git commit -m "feat(ws): replace 2.4s polling with STOMP WebSocket subscription"
```

---

## Task 8: 更新 ROADMAP

**Files:**
- Modify: `ROADMAP.md`

- [ ] **Step 1: 标记 P1 #13 完成，更新进度说明**

在 ROADMAP.md 中，将：
```
| 13 | WebSocket 实时推送 | 替代前端 2.4s 轮询，分析状态变化时主动推送给前端 | Spring WebSocket / STOMP | 1 天 | ★★★★ |
```
改为：
```
| 13 | ~~WebSocket 实时推送~~ | ~~替代前端 2.4s 轮询，分析状态变化时主动推送给前端；STOMP over WebSocket，JWT ChannelInterceptor 验证，convertAndSendToUser 用户路由~~ | ~~Spring WebSocket / STOMP, @stomp/stompjs~~ | **已完成** | ★★★★ |
```

在"已完成"列表中加入 `#13(WebSocket 实时推送)`。

- [ ] **Step 2: 提交**

```bash
git add ROADMAP.md
git commit -m "docs: mark WebSocket push (P1 #13) as completed"
```
