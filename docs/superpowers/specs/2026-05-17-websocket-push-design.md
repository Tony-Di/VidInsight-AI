# WebSocket 实时推送设计文档

**日期**: 2026-05-17  
**目标**: 用 STOMP over WebSocket 替代前端 2.4s 轮询，视频状态变化时后端主动推送给前端。

---

## 背景

当前前端用 `setInterval(2400ms)` 轮询 `GET /api/videos/{id}`，效率低且体验差。后端状态变化点明确（`VideoAnalysisJobServiceImpl`、`VideoImportTaskServiceImpl`），适合改为服务端主动推送。

---

## 技术选型

STOMP over WebSocket（`spring-boot-starter-websocket`）。

选择原因：
- Spring 的 `convertAndSendToUser` 自动处理用户路由，不需要自己维护 session 注册表
- 相比原生 WebSocket 代码量更少，用户隔离开箱即用
- 前端用 `@stomp/stompjs`，约 45 行替换掉现有 30 行轮询

---

## 数据流

```
前端                              后端
  │── GET /ws (HTTP Upgrade) ────►│ SecurityConfig: /ws/** permitAll
  │◄─ 101 Switching Protocols ────│
  │                               │
  │── STOMP CONNECT               │
  │   Authorization: Bearer <jwt> ├─► WebSocketAuthInterceptor
  │                               │   验 JWT → principal = userId.toString()
  │── SUBSCRIBE                   │
  │   /user/queue/video-status    │
  │                               │
  │              [状态变化]        │
  │◄─ MESSAGE ────────────────────│ VideoStatusPushService
  │   /user/queue/video-status    │ .push(userId, VideoStatusPush)
  │   {videoId, videoStatus, ...} │
```

**用户隔离**：principal name = `userId.toString()`，`convertAndSendToUser` 只推给视频所有者，其他用户收不到。

---

## 后端设计

### 新增文件

**`config/WebSocketConfig.java`**
- `@EnableWebSocketMessageBroker`
- STOMP endpoint: `/ws`，`setAllowedOriginPatterns("*")`
- Simple broker: `/queue`、`/topic`
- App destination prefix: `/app`
- User destination prefix: `/user`
- 注册 `WebSocketAuthInterceptor` 到 inbound channel

**`websocket/WebSocketAuthInterceptor.java`**（ChannelInterceptor）
- 拦截 `StompCommand.CONNECT` 帧
- 从 `Authorization` header 取 Bearer token，调 `JwtUtil.parse()`
- 验证通过：构造 `UsernamePasswordAuthenticationToken`，`accessor.setUser(principal)`
- 验证失败：抛 `MessageDeliveryException`，连接被拒绝

**`websocket/VideoStatusPush.java`**（record）
```java
public record VideoStatusPush(
    Long videoId,
    String videoStatus,
    String audioUrl       // null 直到音频就绪
) {}
```

**`websocket/VideoStatusPushService.java`**
```java
public void push(Long userId, VideoStatusPush push) {
    messagingTemplate.convertAndSendToUser(
        userId.toString(),
        "/queue/video-status",
        push
    );
}
```
fail-silent：WebSocket 推送失败不影响主业务（try-catch + warn log）。

### 修改文件

**`pom.xml`**
- 新增 `spring-boot-starter-websocket`

**`config/SecurityConfig.java`**
- `/ws/**` 加入 `permitAll`（WebSocket 握手是 HTTP，需要过 Security filter chain）

**`service/impl/VideoAnalysisJobServiceImpl.java`**
- 注入 `VideoStatusPushService`
- COMPLETED 分支：写库 + evict cache 后，push `{videoId, COMPLETED, audioUrl}`
- FAILED 分支：写库 + evict cache 后，push `{videoId, FAILED, null}`

**`service/impl/VideoImportTaskServiceImpl.java`**
- 注入 `VideoStatusPushService`
- PENDING 分支（导入成功）：push `{videoId, PENDING, null}`
- IMPORT_FAILED 分支：push `{videoId, IMPORT_FAILED, null}`
- 复用分支（MD5 命中）：push `{videoId, COMPLETED, audioUrl}`

Push 调用位置统一放在 `videoCacheService.evict...()` 之后，确保缓存先失效。

---

## 前端设计

### 依赖

```
npm install @stomp/stompjs
```

### 删除

`AppInner` 中 `pollTimerRef` 相关的 useEffect（约 30 行），以及 `pollTimerRef` 的 `useRef` 声明。

### 新增

在 `AppInner` 中，`page` 和 `user` 已知后建立 STOMP 连接：

```typescript
useEffect(() => {
  const token = getStoredToken();
  if (!token) return;

  const client = new Client({
    brokerURL: 'ws://localhost:8080/ws',
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 5000,
    onConnect: () => {
      client.subscribe('/user/queue/video-status', (frame) => {
        const push: VideoStatusPush = JSON.parse(frame.body);
        
        setVideos(curr => {
          const prev = curr.find(v => v.id === push.videoId);
          // IMPORTING → PENDING：触发自动分析
          if (prev?.videoStatus === 'IMPORTING' && push.videoStatus === 'PENDING') {
            void handleStartAnalysis({ id: push.videoId } as VideoInfo);
          }
          return curr.map(v => v.id === push.videoId ? { ...v, ...push } : v);
        });
      });
    },
  });

  client.activate();
  return () => { client.deactivate(); };
}, [user.id]);  // user 变化（登出再登入）时重连
```

### 类型

```typescript
interface VideoStatusPush {
  videoId: number;
  videoStatus: VideoStatus;
  audioUrl: string | null;
}
```

### 不变的部分

- `getVideo(id)` 仍在 detail modal 打开时调用（获取完整 transcript/summary）
- `loadVideos()` 仍在进入 workbench 时调用（初始加载列表）
- 限流、分页、删除逻辑不受影响

---

## 边界情况

| 场景 | 处理方式 |
|------|---------|
| Redis/WebSocket 服务短暂不可用 | push fail-silent，不影响主业务写库；前端仍可手动刷新 |
| JWT 过期后 STOMP 重连失败 | reconnect 超过阈值后不影响页面；JWT 过期由现有 `AUTH_REQUIRED_EVENT` 处理 |
| 用户多 tab 打开 | 每个 tab 独立 STOMP session，都会收到同一条 push（STOMP broker 广播给同一 principal 的所有 session）|
| 视频不在当前页列表中 | `videos.map()` 找不到匹配 id，直接返回原数组，不报错 |

---

## 不在范围内

- SockJS 兼容层（目标是现代浏览器，不需要）
- `/topic` 广播（没有需要全局广播的场景）
- 进度百分比实时推送（stage/pct 仍是前端 cosmetic 动画，后端不感知）
