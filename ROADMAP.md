# VidInsight-AI Roadmap

按优先级和简历价值组织的功能列表。优先级 P0 最高，P3 最低。

- **P0** — 现在就缺、影响功能完整性、必须做
- **P1** — 高简历价值的技术栈扩展
- **P2** — 中等价值、锦上添花
- **P3** — 体验优化、可选

---

## P0：现在功能层面缺失（必做）

| # | 功能 | 描述 | 涉及技术 | 工作量 |
|---|------|------|---------|-------|
| 1 | ~~删除视频~~ | ~~DELETE 接口，同时清理本地视频/音频文件，前端列表加删除按钮~~ | ~~Spring MVC、文件 IO~~ | **已完成** |
| 2 | PROCESSING 卡死恢复 | `StartupRecoveryRunner` 扩展，启动时把 PROCESSING 重置为 FAILED（或重新入队） | Spring ApplicationRunner | 1 小时 |
| 3 | ~~URL 导入 MD5 去重~~ | ~~下载完成后计算 MD5，命中已完成记录则复用，避免重复跑 ASR + AI~~ | ~~MyBatis-Plus~~ | **已完成** |
| 4 | ~~IMPORT_FAILED 重试~~ | ~~前端给 IMPORT_FAILED 状态加"重新导入"按钮，后端复用原 sourceUrl 重新提交~~ | ~~前端 + Spring MVC~~ | **已完成** |
| 5 | ~~视频在线播放~~ | ~~前端 Modal 加 `<video>` 播放器，直接放 `/uploads/videos/*.mp4`~~ | ~~React + HTML5 video~~ | **已完成** |
| 6 | ~~分页参数边界校验~~ | ~~clamp page≥1、1≤pageSize≤100，防止 pageSize=99999999 压垮 DB~~ | ~~Spring Validation~~ | **已完成** |
| 7 | 定时清理失败/孤儿记录 | `@Scheduled` 任务每天清理 IMPORT_FAILED/FAILED 旧记录、孤儿文件、卡死中间状态 | Spring Scheduling、Cron | 1-2 小时 |
| 8 | ~~列表缓存写路径失效~~ | ~~Cache Aside 写策略闭环:delete/upload/import/analyze 完成后都 evict 列表缓存(SCAN+DEL,非 KEYS)。修复"删除后列表 60s 内仍显示已删视频"的脏读 bug~~ | ~~Redis SCAN、Spring Data Redis~~ | **已完成** |

---

## P1：高简历价值技术栈扩展（重点做）

| # | 功能 | 描述 | 涉及技术 | 工作量 | 简历价值 |
|---|------|------|---------|-------|---------|
| 7 | ~~Redis 缓存层~~ | ~~Cache Aside 读写流程 / 防穿透（空值哨兵 + 短 TTL）/ 防雪崩（TTL 随机抖动）/ 防击穿（互斥锁，与 #8 合并实现）~~ | ~~Spring Data Redis、Lettuce、ThreadLocalRandom~~ | **已完成** | ★★★★★ |
| 8 | ~~Redis 分布式锁~~ | ~~MD5 去重场景加分布式锁，防止两个相同视频同时上传各自走完整流水线；同时承接 #7 的缓存击穿（热点 key 回源互斥）；Redisson RLock 实现（WatchDog 自动续期 + 双重检查 + 超时降级）~~ | ~~Redisson RLock~~ | **已完成** | ★★★★★ |
| 9 | Redis 限流 | 接口限流（如导入接口限制每用户每分钟 5 次），Lua 脚本实现令牌桶 | Redis Lua | 半天 | ★★★★ |
| 10 | Redis 进度存储 | 替代之前讨论的"内存 Map"，把 yt-dlp 下载进度 / 分析进度存 Redis，前端轮询读取 | Redis Hash | 半天 | ★★★ |
| 11 | ~~用户体系 + JWT~~ | ~~后端:Spring Security 6 无状态 filter chain / JWT(jjwt 0.12) HS256 24h / BCrypt 密码哈希 / 视频按 user_id 隔离 / MD5 去重按用户限定避免跨用户数据泄漏 / CORS preflight 走独立 CorsConfigurationSource。前端:Auth 页 + token 注入 + 401 全局事件 + 乐观渲染恢复登录态~~ | ~~Spring Security 6、jjwt 0.12、BCrypt、React~~ | **已完成（前后端）** | ★★★★★ |
| 12 | AWS S3 对象存储 | 把本地 uploads 替换为 S3,前端用预签名 URL 直传(绕过后端流量)。S3 key 按用户分前缀(`s3://bucket/user-{id}/...`)。**取代之前的 MinIO 方案**——目标是真公网部署 | AWS SDK v2、预签名 URL | 2 天 | ★★★★★ |
| 13 | WebSocket 实时推送 | 替代前端 2.4s 轮询，分析状态变化时主动推送给前端 | Spring WebSocket / STOMP | 1 天 | ★★★★ |
| 14 | AI 总结流式输出 | SiliconFlow chat 接口改为 stream=true，后端用 SSE 推给前端，实现"打字机效果" | SSE、Reactive | 1 天 | ★★★★ |

---

## P2：中等价值（有时间就做）

| # | 功能 | 描述 | 涉及技术 | 工作量 | 简历价值 |
|---|------|------|---------|-------|---------|
| 15 | ~~Elasticsearch 搜索~~ | ~~对 transcript 和 summary 做全文搜索~~ ❌ **已 drop**:US recruiter 不关注全文搜索优化,数据规模也撑不起 ES 运维复杂度 | — | — | — |
| 16 | Docker 化 | 后端多阶段 Dockerfile、前端 nginx 镜像、完整 docker-compose 一键启动 | Docker、Docker Compose | 半天 | ★★★ |
| 17 | GitHub Actions CI | 自动跑 mvnw test、构建镜像、推送到 GHCR | GitHub Actions | 半天 | ★★★ |
| 18 | Prometheus + Grafana | Actuator 暴露 metrics，Grafana 展示请求 QPS / P99 延迟 / JVM 内存 | Spring Actuator、Micrometer | 1 天 | ★★★ |
| 19 | 链路追踪 | OpenTelemetry / SkyWalking 追踪一次导入到分析完成的完整链路 | OpenTelemetry | 1 天 | ★★★★ |
| 20 | 视频标签 / 分类 | 用户给视频打标签、按标签筛选 | MyBatis-Plus 多表关联 | 1 天 | ★★ |
| 21 | 邮件通知 | 视频分析完成后发邮件提醒 | Spring Mail、模板引擎 | 半天 | ★★ |
| 22 | 导出 transcript / summary | 导出为 Markdown 或 PDF 下载 | iText / 模板引擎 | 半天 | ★★ |

---

## P3：体验和打磨（最后）

| # | 功能 | 描述 | 涉及技术 | 工作量 |
|---|------|------|---------|-------|
| 23 | transcript 带时间戳 | ASR 返回的不只是纯文本，还有每段的起止时间，点击文本可跳转视频播放位置 | 前端 video API | 1 天 |
| 24 | 多语言 ASR | 支持中英日韩等多语言识别，前端选语言 | SiliconFlow 参数 | 半天 |
| 25 | 视频缩略图 | ffmpeg 抽一帧作为列表缩略图 | ffmpeg | 半天 |
| 26 | 摘要风格选择 | 简短摘要 / 详细笔记 / 思维导图 三种风格 | Prompt 工程 | 半天 |
| 27 | 移动端响应式 | 现有 CSS 媒体查询已有基础，再优化 | CSS | 半天 |
| 28 | 部署到公网 | 阿里云 ECS / Vercel + 域名 + HTTPS | 运维 | 1 天 |

---

## 当前进度（截至 2026-05-15）

✅ **已完成**:
- P0 全部除了 #2(PROCESSING 卡死恢复)和 #7(定时清理)
- P1 #7(Redis 缓存层)、#8(Redisson 分布式锁)、#11(JWT 前后端)、新增 #8(列表缓存写路径失效)

⏳ **下一步建议顺序**(距实习结束 2026-07-17 约 9 周):

1. **本周内** — Redis 限流(P1 #9,半天):凑齐"缓存 / 锁 / 限流"三件套,直接按 userId 维度
2. **下周** — WebSocket(P1 #13,1 天)+ SSE 流式摘要(P1 #14,1 天):干掉 2.4s 轮询 + 打字机效果。**这两个做完 demo 视觉效果上一个台阶**
3. **第 3-4 周** — AWS S3(P1 #12,2 天):S3 直传 + 按用户分前缀,顺便解决 `/uploads/**` 公开访问的安全隐患
4. **第 5 周** — Docker 化(P2 #16,半天)+ 公网部署(P3 #28,1 天):产出 live demo URL,简历最关键的交付物
5. **第 6 周后** — P0 #2 #7 这种小活清掉,剩下时间根据情况挑 P2
6. **实习结束(7/17)后** — 3 周休假,只做 LeetCode

⚠️ 已 dropped(不再考虑):MinIO(走 S3)、Elasticsearch、微服务拆分、K8s

---

## 简历项目描述模板

按当前进度,实际能写的版本(全部对应代码里真做了的事):

> **VidInsight-AI** — Full-stack video analysis platform with LLM-powered transcription and summarization
> - **Stack**: Spring Boot 3.5 / Java 21 / MyBatis-Plus / React 19 / MySQL 8 / Redis / RabbitMQ
> - **Auth & data isolation**: Spring Security 6 stateless JWT (jjwt 0.12, HS256, BCrypt), per-user video ownership enforced at every endpoint, MD5 dedup scoped to user to prevent cross-tenant data leak
> - **Caching**: Redis Cache Aside with full write-path invalidation (per-user list keys evicted via SCAN, not KEYS); null-sentinel for penetration; jittered TTL for avalanche; Redisson RLock with WatchDog renewal + double-check for breakdown
> - **Distributed locking**: Redisson RLock atomically dedupes concurrent uploads of the same file, eliminating redundant ASR + LLM calls
> - **Async pipeline**: RabbitMQ-driven two-phase workflow (intake → analysis) with DLQ + idempotent MQ consumer; startup recovery resets orphaned IMPORTING rows
> - **Chunked upload**: 5MB chunks, resumable, MD5 verified on merge

下面这些做完后可以追加(目前还没做):

> - **Rate limiting**: token-bucket via Redis Lua, per-user
> - **Realtime**: WebSocket push replaces 2.4s polling; SSE streams LLM summary tokens
> - **Object storage**: AWS S3 with per-user prefixed pre-signed URLs (frontend → S3 direct, bypasses backend)
> - **Deployment**: Multi-stage Docker + GitHub Actions CI, live demo at `<URL>`
