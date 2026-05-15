# VidInsight-AI 项目流程详解

> 一句话定位:**用户上传/粘贴视频链接 → 后端落盘并抽音 → ASR 转写 → LLM 摘要 → 前端展示**。
> 全链路通过状态机 + MQ 解耦,任意一环故障都有兜底分支。

---

## 0. 目录

1. [模块拓扑与技术栈](#1-模块拓扑与技术栈)
2. [数据模型与数据库](#2-数据模型与数据库)
3. [REST API 全清单](#3-rest-api-全清单)
4. [视频状态机](#4-视频状态机)
5. [Phase 1A —— 切片上传流程](#5-phase-1a--切片上传流程)
6. [Phase 1B —— URL 导入流程(yt-dlp)](#6-phase-1b--url-导入流程yt-dlp)
7. [启动恢复(StartupRecoveryRunner)](#7-启动恢复startuprecoveryrunner)
8. [Phase 2 —— RabbitMQ 异步分析流水线](#8-phase-2--rabbitmq-异步分析流水线)
9. [MQ 拓扑与 DLQ 契约](#9-mq-拓扑与-dlq-契约)
10. [文件存储、MD5 去重与删除](#10-文件存储md5-去重与删除)
11. [前端流程(单页 + 轮询)](#11-前端流程单页--轮询)
12. [配置项 / 环境变量速查](#12-配置项--环境变量速查)
13. [关键设计要点与陷阱](#13-关键设计要点与陷阱)

---

## 1. 模块拓扑与技术栈

```
┌──────────────────────────────────────────────────────────────┐
│  Browser ── React 19 + Vite + Ant Design 6 (5173)           │
│            ↳ src/api.ts(fetch + ApiResponse 解封装)         │
│            ↳ src/App.tsx(单页双路由 + 2.4s 轮询)            │
└─────────────────────────┬────────────────────────────────────┘
                          │  /api  /uploads  (Vite proxy → :8080)
┌─────────────────────────▼────────────────────────────────────┐
│  Spring Boot 3.5 / Java 21 / Maven(8080)                    │
│                                                              │
│  Controller ── Service ── Mapper(MyBatis-Plus 3.5.9)        │
│                  │                                          │
│                  ├── @Async analysisTaskExecutor            │  Phase 1
│                  │     (core=2 / max=4 / queue=100)         │
│                  │                                          │
│                  └── RabbitMQ Producer / Consumer           │  Phase 2
│                        ↳ DLX/DLQ 兜底                       │
│                                                              │
│  外部进程:yt-dlp(下载)、ffmpeg(抽音)                  │
│  外部 HTTP:SiliconFlow 平台                                │
│       ├─ /audio/transcriptions   TeleAI/TeleSpeechASR       │
│       └─ /chat/completions       deepseek-ai/DeepSeek-V4-Flash │
└──────┬─────────────────┬────────────────┬───────────────────┘
       │                 │                │
   MySQL 8 :3306    RabbitMQ 3 :5672    uploads/
   db: video_insight  mgmt :15672      ├─ videos/
   root/123456       guest/guest        ├─ audio/
                                        └─ chunks/{uploadId}/N.part
```

**仓库布局**

| 目录 | 内容 |
|------|------|
| `video-insight-backend/` | Spring Boot 服务,Java 21,Maven 包装器 `mvnw` |
| `video-insight-frontend/` | React 19 SPA,Vite,Ant Design 6 |
| `docker-compose.yml` | MySQL 8 + RabbitMQ 3-management |
| `uploads/` | 本地存储根(`videos` / `audio` / `chunks`) |
| `video-insight-backend/src/main/resources/sql/` | 手动执行的 SQL 迁移(无 Flyway/Liquibase) |

---

## 2. 数据模型与数据库

### 2.1 表 `video_info`(`com.videoinsight.backend.entity.VideoInfo`)

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `bigint AUTO_INCREMENT` | 主键 |
| `title` | `varchar` | 视频标题(用户填或文件名/URL) |
| `source_url` | `varchar(500)` | 本地路径 `/uploads/videos/xxx.mp4` 或外部 URL |
| `file_md5` | `varchar(32)`,带索引 | 视频文件 MD5,用于去重 |
| `audio_url` | `varchar(500)` | 抽出的 mp3,`/uploads/audio/{id}.mp3` |
| `transcript` | `longtext` | ASR 转写文本 |
| `status` | `varchar`(枚举 `VideoStatus`) | 状态机当前节点(注意 Java 字段名是 `videoStatus`,DB 列名是 `status`) |
| `summary` | `text` | LLM 摘要;**失败时复用此字段写错误根因** |
| `created_at` / `updated_at` | `datetime` | 时间戳 |

### 2.2 表 `video_upload_task`(`VideoUploadTask`)

切片上传期间的临时任务表,字段简表:

| 字段 | 含义 |
|------|------|
| `upload_id` | UUID,init 时返回给前端 |
| `title` / `file_name` / `total_chunks` | 元数据 |
| `uploaded_chunks` | 已上传分片数(用于显示进度) |
| `status` | `UploadTaskStatus` 枚举(`UPLOADING` / `COMPLETED` / `FAILED`) |

### 2.3 SQL 迁移历史(`src/main/resources/sql/`)

```
20260510_add_video_analysis_details.sql    # 加 audio_url + transcript 列
20260512_add_file_md5.sql                   # 加 file_md5 + 索引
```

> ⚠️ **没有 Flyway/Liquibase**——加字段需手动 `mysql -uroot -p123456 video_insight < xxx.sql`。

---

## 3. REST API 全清单

| 方法 | 路径 | Controller | 用途 |
|------|------|-----------|------|
| `POST` | `/api/videos` | `VideoInfoController#createVideo` | 仅创建一条记录(挂个外部 URL,不下载) |
| `POST` | `/api/videos/import-url` | `VideoInfoController#importVideo` | URL 导入:落库 IMPORTING + 后台 yt-dlp 下载 |
| `POST` | `/api/videos/{id}/retry-import` | `VideoInfoController#retryImport` | 失败的导入重试(仅限 `IMPORT_FAILED`) |
| `POST` | `/api/videos/upload` | `VideoInfoController#uploadVideo` | 单次小文件上传(直接 multipart) |
| `GET`  | `/api/videos` | `VideoInfoController#listVideos` | 分页列表 |
| `GET`  | `/api/videos/{id}` | `VideoInfoController#getVideoDetail` | 单条详情(前端轮询用) |
| `POST` | `/api/videos/{id}/analyze` | `VideoInfoController#analyzeVideo` | 触发 Phase 2:状态翻 PROCESSING + 投 MQ |
| `DELETE` | `/api/videos/{id}` | `VideoInfoController#deleteVideo` | 删记录;无其他记录引用同物理文件时连带删文件 |
| `POST` | `/api/videos/chunks/init` | `VideoUploadController#initChunkUpload` | 切片上传:申请 uploadId |
| `POST` | `/api/videos/chunks/upload` | `VideoUploadController#uploadChunk` | 切片上传:上传第 N 片 |
| `POST` | `/api/videos/chunks/complete` | `VideoUploadController#completeChunkUpload` | 切片上传:合并 + MD5 去重 + 落记录 |
| `GET` | `/uploads/**` | Spring static handler | 直接对外暴露 `uploads/` 目录(供前端 `<video src>` / 音频下载) |

**统一响应包装** `ApiResponse<T> { code, message, data }`,前端 `request<T>()` 校验 `code === 200`。

---

## 4. 视频状态机

```
       ┌──────────────┐ 切片上传(同步,接口内完成)
       │              │
入口① ▶│  UPLOADING   │── merge + MD5 ──┐
       │              │                  │
       └──────────────┘                  │
                                         ▼
       ┌──────────────┐  yt-dlp 下载  ┌─────────┐
入口② ▶│  IMPORTING   │── 成功 ───────▶  PENDING ◀── 直接 POST /upload
       │              │                └────┬────┘    (单次小文件,不进 IMPORTING)
       └──────┬───────┘                     │
              │                             │ POST /{id}/analyze
              │ 失败 / 启动恢复              │     (前端自动 or 手动)
              ▼                             ▼
       ┌──────────────┐              ┌─────────────┐
       │ IMPORT_FAILED│              │ PROCESSING  │── 异常 ──▶ FAILED
       └──────┬───────┘              └──────┬──────┘   (DLQ 记录告警)
              │                             │
              │ POST /{id}/retry-import     │ 三步流水成功
              ▼                             ▼
        重新进 IMPORTING             ┌─────────────┐
                                     │  COMPLETED  │
                                     └─────────────┘
```

**状态守卫**(`VideoInfoServiceImpl.analyzeVideo`)

```java
if (status != PENDING && status != FAILED) {
    throw new BusinessException(400, "video status does not allow analysis");
}
```

也就是说:已 `COMPLETED` / 仍在 `PROCESSING` / 还在 `IMPORTING` 的视频,**调 analyze 会被拒**。

---

## 5. Phase 1A —— 切片上传流程

### 5.1 时序

```
前端 uploadVideoChunked(file, title, onProgress)
  │  CHUNK_SIZE = 5 MB
  │  totalChunks = ceil(file.size / 5MB)
  │
  ├─► POST /api/videos/chunks/init
  │      body: { title, fileName, totalChunks }
  │      ──► VideoUploadTaskServiceImpl.initChunkUpload
  │            ① validateVideoFilename(扩展名 ∈ {mp4,mov,avi,mkv,webm})
  │            ② INSERT video_upload_task (status=UPLOADING)
  │            ③ return uploadId (UUID)
  │
  ├─► for i in 0..totalChunks-1
  │      POST /api/videos/chunks/upload?uploadId=...&chunkIndex=i
  │      multipart file
  │      ──► VideoUploadTaskServiceImpl.uploadChunk
  │            ① 校验 chunkIndex 范围,任务状态=UPLOADING
  │            ② FileStorageService.saveChunk
  │                 → uploads/chunks/{uploadId}/{i}.part
  │                 (重复上传同一片返回 alreadyUploaded=true,不增加计数)
  │            ③ uploadedChunks++(去重感知),回写 task
  │            ④ return { uploadedChunks, totalChunks }
  │      onProgress?.(round((i+1)/total * 95))   // 留 5% 给合并
  │
  └─► POST /api/videos/chunks/complete?uploadId=...
         ──► VideoUploadTaskServiceImpl.completeChunkUpload
               ① 校验所有 N.part 都存在(ensureAllChunksExist)
               ② 顺序拼接 → uploads/videos/{uuid}.{ext}(OutputStream)
               ③ MD5(整个合并文件)── FileHashUtil.md5
               ④ findCompletedByMd5(md5) 命中 → 复用旧 VideoInfo
                    ↳ 删 chunks 临时目录,task=COMPLETED,直接返回旧记录
               ⑤ 未命中 → INSERT video_info(status=PENDING, file_md5=xxx)
               ⑥ task=COMPLETED,删 chunks,返回新 VideoInfo
         异常分支:任何步骤抛错 → task=FAILED,抛 IllegalStateException

前端拿到 VideoInfo(PENDING)立即调 POST /{id}/analyze ── Phase 2 入口
```

### 5.2 关键文件

- `controller/VideoUploadController.java`
- `service/impl/VideoUploadTaskServiceImpl.java`
- `service/impl/LocalFileStorageServiceImpl.java`(`saveChunk` / `mergeChunks` / `deleteChunks`)
- `util/FileHashUtil.java`(流式 MD5,不一次性读入内存)

### 5.3 注意

- **Spring Boot 上传体积上限**:`spring.servlet.multipart.max-file-size=200MB` / `max-request-size=500MB`(配置在 `application.properties`)。这是单次 multipart 的限制,切片本身只有 5 MB 不会触发。
- 路径穿越防护:每个 `Path.resolve()` 之后都做 `.normalize()` + `startsWith(rootPath)` 检查,新增文件操作代码必须保留这个模式。
- 合并是**纯字节拼接**,不做容器层校验。前端切片必须严格按字节序提交。

---

## 6. Phase 1B —— URL 导入流程(yt-dlp)

### 6.1 时序

```
前端 importVideoByUrl(title, sourceUrl)
  │
  └─► POST /api/videos/import-url
         body: { title, sourceUrl }
         ──► VideoImportServiceImpl.importVideo
               ① INSERT video_info (status=IMPORTING, source_url=原始链接)
               ② videoImportTaskService.submitImport(id, sourceUrl)  ← @Async
               ③ 立即 return VideoInfo (前端拿到 id 后即可开始轮询)

[异步线程 analysisTaskExecutor]
VideoImportTaskServiceImpl.submitImport(@Async)
  ① YtDlpVideoDownloadServiceImpl.download(sourceUrl)
       - 创建临时目录 video-import-{uuid}/
       - 调 yt-dlp 子进程(命令见下),输出 → 临时目录/{uuid}.mp4
       - 子进程日志 → 临时目录/yt-dlp.log
       - 等待 ≤ ytdlp.download-timeout-minutes(默认 30 分钟)
       - exit != 0 或 timeout → 抛 IllegalStateException(带日志摘要)
  ② MD5 整个下载文件
  ③ findCompletedByMd5 命中 → 直接复用 audioUrl/transcript/summary,
      状态写 COMPLETED,UPDATE 当前行后返回(跳过 Phase 2!)
  ④ 未命中 → fileStorageService.saveVideo(downloadedFile, ...)
       - 复制到 uploads/videos/{uuid}.{ext}
       - 校验扩展名 ∈ ALLOWED_EXTENSIONS
  ⑤ UPDATE video_info SET source_url=本地路径, file_md5=..., status=PENDING
  finally:
       deleteTempFile(downloadedFile) → 删临时文件 + 临时目录
异常路径:
       UPDATE video_info SET status=IMPORT_FAILED, summary=getRootCauseMessage(ex)
```

### 6.2 yt-dlp 命令构造(`YtDlpVideoDownloadServiceImpl#buildCommand`)

```
yt-dlp
  --no-playlist
  --no-check-certificate
  --merge-output-format mp4         # 容器层 merge,不重新编码(对比 --recode-video 慢 10×+)
  -f "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=720]+bestaudio/best[height<=720]"
  --user-agent "Mozilla/5.0 ..."     # 反爬规避
  -o {tempFile}
  [--ffmpeg-location ${YT_DLP_FFMPEG_LOCATION}]   # yt-dlp 找不到 ffmpeg 时手动指
  {sourceUrl}
```

> ⚠️ **不要换回 `--recode-video`**——会触发整片重编码,B 站/油管几十分钟视频变 30 分钟+ CPU 满载。`--merge-output-format mp4` 只在容器层 mux,秒级完成。

### 6.3 重试入口

```
POST /api/videos/{id}/retry-import
  ── VideoImportServiceImpl.retryImport
       ① 校验 status == IMPORT_FAILED
       ② 校验 source_url 仍是 http(s) 链接
       ③ status = IMPORTING, summary = null
       ④ submitImport(id, originalUrl) ── 再走一遍 6.1
```

### 6.4 关键文件

- `controller/VideoInfoController.java#importVideo / retryImport`
- `service/impl/VideoImportServiceImpl.java`
- `service/impl/VideoImportTaskServiceImpl.java`
- `service/impl/YtDlpVideoDownloadServiceImpl.java`
- `config/YtDlpProperties.java`

---

## 7. 启动恢复(StartupRecoveryRunner)

`service/impl/StartupRecoveryRunner.java`,实现 `ApplicationRunner`,Spring Boot 启动末尾运行:

```sql
UPDATE video_info
   SET status = 'IMPORT_FAILED',
       summary = 'Import interrupted by service restart',
       updated_at = NOW()
 WHERE status = 'IMPORTING';
```

**为什么只重置 `IMPORTING`**

| 状态 | 重启后能否自愈 | 原因 |
|------|---------------|------|
| `IMPORTING` | ❌ 必须重置 | yt-dlp 子进程随 JVM 退出而终止,DB 里这一行会永远卡住 |
| `PROCESSING` | ✅ 自愈 | RabbitMQ 消息消费者未 ACK 即崩溃 → 消息回到队列,重启后自动 redeliver |
| 其它 | ✅ | 终态或可手动操作 |

---

## 8. Phase 2 —— RabbitMQ 异步分析流水线

### 8.1 触发链

```
前端(自动 or 手动)
  └─► POST /api/videos/{id}/analyze
        ──► VideoInfoServiceImpl.analyzeVideo
              ① 状态守卫: PENDING || FAILED
              ② UPDATE status = PROCESSING
              ③ videoAnalysisTaskService.submitAnalysis(id)
                   └─► VideoAnalysisProducer.send(id)
                         rabbitTemplate.convertAndSend(
                           exchange = video.analysis.exchange,
                           routingKey = video.analysis,
                           new VideoAnalysisMessage(id)        // Jackson2Json 序列化
                         )
              ④ return VideoInfo(状态已是 PROCESSING)
```

### 8.2 消费链

```
@RabbitListener(queues = "video.analysis.v2.queue")
VideoAnalysisConsumer.consume(VideoAnalysisMessage msg)
  └─► VideoAnalysisJobServiceImpl.executeAnalysis(videoId)
        ① 幂等:status == COMPLETED → 直接 return(MQ 重投兼容)
        ② try {
             VideoAnalysisResult result = videoAnalysisService.analyze(videoInfo)
                ↳ ① mediaProcessingService.extractAudio(videoInfo)
                ↳ ② speechRecognitionService.transcribe(audioUrl)
                ↳ ③ aiSummaryService.summarize(transcript)
             UPDATE video_info SET
                status=COMPLETED, audio_url=..., transcript=..., summary=...
           } catch (Exception ex) {
             UPDATE video_info SET status=FAILED, summary=getRootCauseMessage(ex)
             // 注意:不 rethrow → 消息正常 ACK,不进 DLQ
           }
```

> ⚠️ 当前代码里业务异常被 try/catch 吞掉,**只有 `executeAnalysis` 之外的异常**(比如 `selectById` DB 宕机)才会冒到 `consume` 触发 DLQ。也就是说:DLQ 主要兜的是「连 DB 都连不上」这类基础设施故障,业务失败走 `FAILED` 状态。

### 8.3 三步流水细节

#### Step 1 —— 抽音(`FfmpegMediaProcessingServiceImpl`)

```
ffmpeg -y
  -i {videoPath}
  -vn                  # 去视频流
  -ar 16000            # 采样率 16 kHz(ASR 标配)
  -ac 1                # 单声道
  -b:a 64k             # 64 kbps 比特率
  uploads/audio/{videoId}.mp3
  超时:5 分钟
  exit != 0 → IllegalStateException
返回:"/uploads/audio/{videoId}.mp3"
```

#### Step 2 —— ASR(`SiliconFlowSpeechRecognitionServiceImpl`)

```
POST https://api.siliconflow.cn/v1/audio/transcriptions
Authorization: Bearer ${SILICONFLOW_API_KEY}
Content-Type: multipart/form-data; boundary=----VidInsightBoundary{uuid}
Body:
  --boundary
  Content-Disposition: form-data; name="model"

  TeleAI/TeleSpeechASR
  --boundary
  Content-Disposition: form-data; name="file"; filename="{videoId}.mp3"
  Content-Type: application/octet-stream

  <整个 mp3 二进制>
  --boundary--
超时:10 分钟
解析:JSON.text 字段
```

> 实现使用 JDK 原生 `java.net.http.HttpClient`,**整个 mp3 一次性 readAllBytes 进内存** —— 后续音频文件大时需要改流式上传。

#### Step 3 —— 摘要(`SiliconFlowAiSummaryServiceImpl`)

```
POST https://api.siliconflow.cn/v1/chat/completions
Authorization: Bearer ${SILICONFLOW_API_KEY}
Content-Type: application/json
Body:
{
  "model": "deepseek-ai/DeepSeek-V4-Flash",
  "stream": false,
  "messages": [
    { "role": "system", "content":
        "You are a professional video content analyst.
         Summarize the transcript into clear Markdown with:
         1. Core summary
         2. Key insights
         3. Important quotes if any
         4. Topic tags
         Be concise, factual, and structured." },
    { "role": "user", "content": "{transcript}" }
  ]
}
超时:20 分钟
解析:choices[0].message.content
```

---

## 9. MQ 拓扑与 DLQ 契约

### 9.1 拓扑(`config/RabbitMqConfig.java`)

```
主链路:
   Exchange (DirectExchange, durable)
     name = video.analysis.exchange
        ↓ routingKey = video.analysis
   Queue (durable)
     name = video.analysis.v2.queue
     args = {
       x-dead-letter-exchange:    video.analysis.dlx
       x-dead-letter-routing-key: video.analysis.dead
     }

死信兜底:
   Exchange (DirectExchange, durable)
     name = video.analysis.dlx
        ↓ routingKey = video.analysis.dead
   Queue (durable)
     name = video.analysis.dlq
```

### 9.2 消费契约

```properties
spring.rabbitmq.listener.simple.prefetch=1
spring.rabbitmq.listener.simple.default-requeue-rejected=false
```

加上 `RabbitMqConfig#rabbitListenerContainerFactory` 里再次显式 `factory.setDefaultRequeueRejected(false)`,意味着:

- 监听器正常返回 → AutoAck,消息从主队列删除
- 监听器抛出异常 → 不重入主队列(避免无限循环) → 因 queue 带 DLX 参数,消息**自动**路由到 DLQ
- DLQ 也有消费者 `consumeDlq`(`VideoAnalysisConsumer.java:33`),只打一条 ERROR 日志,不做补偿

### 9.3 改 queue 配置时的硬约束

> **如果你修改了 `videoAnalysisQueue()` 的任何参数(DLX 路由、TTL、长度限制等),必须先去 RabbitMQ 管理界面(http://localhost:15672)删除老队列,再重启 Spring Boot。否则启动会因 `PRECONDITION_FAILED - inequivalent arg` 失败。**

理由:RabbitMQ 不允许用不同参数重新声明同名队列。

### 9.4 监听器与 Async 共用线程池

`RabbitMqConfig` 把 `analysisTaskExecutor`(用于 `@Async` 的 yt-dlp 下载)同时设给了 `SimpleRabbitListenerContainerFactory#setTaskExecutor`,所以 MQ 消费者也跑在 `analysis-task-*` 这组线程上。core=2 / max=4 的池子被两类任务共享 —— 当下载和分析并发量大时需要扩容。

---

## 10. 文件存储、MD5 去重与删除

### 10.1 存储布局

```
uploads/
├── videos/
│   └── {uuid}.{ext}                 # 最终落盘的视频(切片合并 or yt-dlp 下载结果)
├── audio/
│   └── {videoId}.mp3                # ffmpeg 抽音结果(以 video.id 命名)
└── chunks/
    └── {uploadId}/
        ├── 0.part
        ├── 1.part
        └── ...                       # 合并完成后被 deleteChunks 清理
```

### 10.2 路径穿越防护(`LocalFileStorageServiceImpl`)

每一处文件操作的样板:

```java
Path rootPath = Path.of(rootDir).toAbsolutePath().normalize();
Path target  = rootPath.resolve(filename).normalize();
if (!target.startsWith(rootPath)) {
    throw new IllegalArgumentException("invalid path");
}
```

写新文件操作时**必须遵循此模式**。FfmpegMediaProcessingServiceImpl 也用同样模式。

### 10.3 MD5 去重的三个触点

| 入口 | 触发位置 | 命中后行为 |
|------|---------|-----------|
| 单次上传 | `VideoInfoServiceImpl.uploadVideo` | 直接返回旧记录,不创建新行 |
| 切片上传 | `VideoUploadTaskServiceImpl.completeChunkUpload` | 删 chunks,task=COMPLETED,返回旧记录 |
| URL 导入 | `VideoImportTaskServiceImpl.submitImport` | 复用旧记录的 audioUrl/transcript/summary,**当前这一行直接 COMPLETED**(不再走 Phase 2) |

> 📌 CLAUDE.md 之前写的「URL 导入路径不做 MD5 去重」**已经过时**——代码里实际做了去重,而且是「下载后才算 hash,所以省的是 ASR + LLM 钱,不是带宽」。

`findCompletedByMd5(md5)` 的 SQL 大致是 `SELECT * FROM video_info WHERE file_md5=? AND status='COMPLETED' LIMIT 1`(`VideoInfoMapper`)。

### 10.4 删除时的引用计数

`VideoInfoServiceImpl.deleteVideo` 删一行后:

```java
long sourceRefs = lambdaQuery().eq(VideoInfo::getSourceUrl, sourceUrl).count();
if (sourceRefs == 0) fileStorageService.deleteFile(sourceUrl);
// 同样的检查再对 audioUrl 做一遍
```

—— 因为 MD5 去重可能让多条 `video_info` 共享同一个物理文件(URL 导入复用时复制了 `sourceUrl` 字符串),不能贸然把文件删掉。

---

## 11. 前端流程(单页 + 轮询)

### 11.1 路由

无 React Router,靠 `window.history.pushState` + `popstate` 在两个 `Page` 之间切换:

- `home`:上传 / 粘贴链接的入口
- `workbench`:任务列表 + 任务详情面板

### 11.2 关键交互

#### 提交本地文件(`handleUpload`)

```
uploadVideoChunked(file, title, setUploadProgress)   // 切片上传
   ↓ VideoInfo (PENDING)
analyzeVideo(created.id)                              // 立即触发 Phase 2
   ↓ VideoInfo (PROCESSING)
setActiveVideo(...)
loadVideos(1, true)
navigateTo('workbench')
```

> 上传成功后**前端立即同步调 analyze**,不依赖轮询。

#### 提交 URL(URL 表单回调)

```
importVideoByUrl(title, sourceUrl)
   ↓ VideoInfo (IMPORTING)
setActiveVideo(...)         // 进入轮询状态
navigateTo('workbench')
```

> URL 路径**不直接调 analyze**,因为视频还没下载完。等待轮询里的 `IMPORTING → PENDING` 状态跳变触发自动 analyze。

### 11.3 轮询逻辑(`App.tsx:258-292`)

```ts
const POLLING_STATUSES = new Set<VideoStatus>(['IMPORTING', 'PROCESSING']);

useEffect(() => {
  if (!activeVideo || !POLLING_STATUSES.has(activeVideo.videoStatus)) return;

  pollTimerRef.current = window.setInterval(async () => {
    const latest = await getVideo(activeVideo.id);

    // 关键跳变:IMPORTING → PENDING,自动触发分析
    if (activeVideo.videoStatus === 'IMPORTING' && latest.videoStatus === 'PENDING') {
      const analyzing = await analyzeVideo(latest.id);
      setActiveVideo(analyzing);
      return;
    }

    setActiveVideo(latest);
    if (!POLLING_STATUSES.has(latest.videoStatus)) clearInterval(pollTimerRef.current);
  }, 2400);
}, [activeVideo]);
```

要点:

- **轮询粒度是「当前激活的 activeVideo 一条」**,不是列表里所有视频
- 间隔 2400 ms
- `IMPORTING → PENDING` 跳变是全项目**唯一**自动触发 analyze 的地方(其它入口都是用户操作)
- 状态进入终态(`COMPLETED` / `FAILED` / `IMPORT_FAILED`)即停轮询

### 11.4 双语

`TRANSLATIONS = { zh: {...}, en: {...} }`,语言通过 `localStorage['vi-lang']` 持久化,`t(key)` helper 在组件里翻译文案。

### 11.5 Vite 代理(`vite.config.ts`)

`/api` 与 `/uploads` 代理到 `http://localhost:8080`,前端开发服务器只跑 5173 端口。

---

## 12. 配置项 / 环境变量速查

### 12.1 后端必需环境变量(否则相应流程会失败)

| 变量 | 用途 | 默认 |
|------|------|------|
| `SILICONFLOW_API_KEY` | ASR + 摘要的鉴权 | (空,缺失则 Step 2/3 抛异常) |
| `YT_DLP_PATH` | yt-dlp 可执行文件路径 | `yt-dlp`(走 PATH) |
| `FFMPEG_PATH` | ffmpeg 可执行文件路径 | `ffmpeg`(走 PATH) |
| `YT_DLP_FFMPEG_LOCATION` | 当 yt-dlp 找不到 ffmpeg 时显式指 | (空) |
| `YT_DLP_DOWNLOAD_TIMEOUT_MINUTES` | yt-dlp 单次下载超时 | `30` |
| `RABBITMQ_HOST/PORT/USERNAME/PASSWORD` | MQ 连接 | `localhost:5672 / guest/guest` |

### 12.2 SiliconFlow 模型

```properties
ai.siliconflow.base-url=https://api.siliconflow.cn/v1
ai.siliconflow.asr-model=TeleAI/TeleSpeechASR
ai.siliconflow.chat-model=deepseek-ai/DeepSeek-V4-Flash
```

### 12.3 上传限制

```properties
spring.servlet.multipart.max-file-size=200MB
spring.servlet.multipart.max-request-size=500MB
```

### 12.4 MQ 命名约定(可被环境变量覆盖)

```
exchange    : video.analysis.exchange
queue       : video.analysis.v2.queue   # 注意带 .v2 后缀,前一版被废弃过
routingKey  : video.analysis
DLX         : video.analysis.dlx
DLQ         : video.analysis.dlq
DLQ rk      : video.analysis.dead
```

---

## 13. 关键设计要点与陷阱

### 13.1 两阶段解耦的双层意义

| 解耦维度 | Phase 1(intake) | Phase 2(analysis) |
|---------|-----------------|-------------------|
| **传输** | 同进程 `@Async` 线程池 | RabbitMQ 跨进程消息 |
| **持久化** | DB 状态 `IMPORTING` | DB 状态 `PROCESSING` |
| **失败语义** | `IMPORT_FAILED`,可调 `/retry-import` 重试 | `FAILED`(业务失败) / DLQ(基础设施失败) |
| **重启恢复** | `StartupRecoveryRunner` 主动重置 | MQ 未 ACK 自动 redeliver |

这种「失败原因不混在一起,恢复路径也不混在一起」的设计是这个项目的核心架构卖点。

### 13.2 幂等是显式做的

`VideoAnalysisJobServiceImpl.executeAnalysis` 入口判断 `status == COMPLETED → return`。这一行是**唯一**保护点:RabbitMQ 在 ACK 之前消费者崩溃就会 redeliver,如果不做幂等,ASR + LLM 会被白调一次(花钱)。

### 13.3 业务异常被吞掉的副作用

`executeAnalysis` 里 `try { ... } catch (Exception ex) { 写 FAILED }` 不 rethrow,所以:

- ASR 失败 → 状态 `FAILED`,`summary` 里写错误根因,**消息正常 ACK**
- DLQ **几乎不会**因业务问题被触发,只兜底基础设施级故障

如果未来希望「ASR 失败要进 DLQ 后续人工补偿」,需要在 `executeAnalysis` 里 rethrow,并配合 `try-with-resources` 改写状态写入逻辑。

### 13.4 MyBatis-Plus 分页

代码里用的是裸的 `lambdaQuery().last("LIMIT " + size + " OFFSET " + offset)`,**没有注册** `MybatisPlusInterceptor` / `PaginationInnerInterceptor`(因为 3.5.9 这个 starter 里那个类不一定可用)。

新增分页接口时:

```java
int safePage = Math.max(1, page);
int safePageSize = Math.min(100, Math.max(1, pageSize));
// 必须自己 clamp,不要把用户输入直接拼 SQL
```

### 13.5 同步 vs 流式

ASR 实现里 `Files.readAllBytes(audioPath)` 把整个 mp3 读进内存。视频时长 1 小时左右、音频 64 kbps → 约 27 MB,目前 OK,但**任何把音频码率/时长上限调高的修改**都需要同步把这里改成流式 multipart。

### 13.6 跨平台(Windows / *nix)

代码用 `Path.of` + `.normalize()`,且 yt-dlp / ffmpeg 路径走环境变量,理论上跨平台。但目前主要在 Windows 11 上运行(见根目录),Linux/Mac 部署时:

- 环境变量里的 `YT_DLP_PATH` / `FFMPEG_PATH` 通常用绝对路径,避免 PATH 解析差异
- `uploads/` 目录权限要确保 Spring Boot 进程可写
- RabbitMQ / MySQL 端口默认就够用

### 13.7 没有的东西(刻意不做的)

- ❌ **没有用户系统、JWT、权限**(单人本地工具阶段)
- ❌ **没有 Redis 缓存**(列表查询直接打 MySQL)
- ❌ **没有 WebSocket / SSE**(全靠 2.4s 轮询)
- ❌ **没有 S3 / 云存储**(只有 `uploads/` 本地目录)
- ❌ **没有 Flyway / Liquibase**(SQL 手动跑)
- ❌ **没有 ES / 全文搜索**

这些都在 ROADMAP 里属于待加项(参考根目录 `ROADMAP.md`)。

---

## 14. 一图回顾

```
┌─────────────┐  upload(切片)  ┌────────────┐
│   Browser   │────────────────▶│ Controller │
│  (5173)     │  importUrl/url  │            │
└──────┬──────┘                 └──────┬─────┘
       │ poll 2.4s every {id}          │
       │                               ▼
       │                       ┌───────────────┐
       │                       │   Service     │
       │                       │ ┌───────────┐ │
       │                       │ │ @Async    │ │ Phase 1
       │                       │ │ yt-dlp    │ │  (intake)
       │                       │ └─────┬─────┘ │
       │                       └───────┼───────┘
       │                               │
       │                       UPDATE status
       │                               │
       │                               ▼  PENDING
       │   POST /{id}/analyze   ┌───────────────┐
       └──────────────────────▶ │   Service     │
                                │ analyzeVideo()│
                                └──────┬────────┘
                                       │ Producer
                                       ▼
                          ┌───────────────────────┐
                          │  RabbitMQ             │
                          │  exchange→queue→DLQ   │
                          └──────────┬────────────┘
                                     │ Consumer
                                     ▼
                          ┌────────────────────┐
                          │ executeAnalysis    │
                          │  ① ffmpeg 抽音    │ Phase 2
                          │  ② SiliconFlow ASR │ (analysis)
                          │  ③ DeepSeek 摘要  │
                          └──────────┬─────────┘
                                     │
                          UPDATE status=COMPLETED
                                     │
                                     ▼
                          MySQL  video_info row
                          (transcript / summary 落库)
```

---

**文档基线提交版本**:`a53f698 frontend updated`(本文档基于该 commit 时点的代码梳理,后续若架构演进请同步更新本文件 §13 的「没有的东西」清单和 §3 的 API 表)。
