# VidInsight-AI — Local Benchmarks & Verification

Measured numbers for the backend's distributed-systems features (cache-aside,
RabbitMQ pipeline, token-bucket rate limiting, MD5 dedup). Every number below was
produced by actually running the app against real infrastructure — nothing here is
estimated. Items that could **not** be measured locally are explicitly marked.

> ⚠️ **Honesty note for resume use:** these are single-node, client-limited
> micro-benchmarks. The meaningful takeaways are the **relative** comparisons
> (cache hit vs miss, limiter before vs after fix), each measured under identical
> conditions. Don't quote them as production-scale throughput.

---

## 1. Environment

| Component | Value |
|-----------|-------|
| Code | `git d35cd94` (origin/main) + rate-limiter fix (§7) |
| Runtime | Spring Boot 3.5.14, Java 21 (Dragonwell), single JVM |
| Infra (docker-compose) | MySQL 8.4, RabbitMQ 3-management, Redis 7, MinIO |
| Storage provider | `local` (`APP_STORAGE_PROVIDER=local`) |
| Load generator | `benchmarks/loadtest.py` — stdlib-only closed-loop tester, 50 worker threads |
| Test data | 1,000 `video_info` rows (one user), realistic transcript/summary payloads |
| Host | Windows 11; client and server on the same machine |

Reproduce:

```bash
docker compose up -d                       # MySQL + RabbitMQ + Redis + MinIO
cd video-insight-backend && ./mvnw -DskipTests clean package
APP_STORAGE_PROVIDER=local java -jar target/backend-0.0.1-SNAPSHOT.jar
# register a user, seed rows, then run benchmarks/*.py (see each section)
```

---

## 2. Redis cache-aside — `GET /api/videos/{id}`

Read path: `getDetailOrLoad()` → Redis GET → on miss, Redisson lock + double-check +
DB load + cache populate. Cache layer adds TTL jitter (anti-avalanche), a null
sentinel (anti-penetration), and a distributed lock (anti-stampede).

**A/B at concurrency = 50:**

| Metric | Cache MISS (DB path) | Cache HIT (Redis) | Improvement |
|--------|---------------------:|------------------:|------------:|
| Throughput | 928 req/s | **2,513 req/s** | **2.7×** |
| Latency p50 | 30.4 ms | 18.7 ms | 1.6× lower |
| Latency p95 | 62.7 ms | 29.6 ms | **2.1× lower** |
| Latency p99 | 381.8 ms | 39.9 ms | **9.6× lower** |
| Mean | 49.0 ms | 19.4 ms | 2.5× lower |

The biggest win is tail latency: the miss path's p99 (382 ms) collapses to ~40 ms on
the hit path, because misses pay the Redisson lock + DB round-trip + cache write.
A repeat run was consistent (829 → 2,508 req/s).

```bash
python benchmarks/loadtest.py cache-miss --token "$T" --conc 50 --total 1000
python benchmarks/loadtest.py cache-hit  --token "$T" --conc 50 --total 5000
```

> Note: Redis `INFO` keyspace_hits/misses is **not** reported as the cache hit rate —
> Redisson lock ops share the same Redis and pollute that counter. In the hit
> benchmark, all reads after warm-up are cache hits by construction.

---

## 3. Token-bucket rate limiter — `POST /api/auth/login` (capacity = 5 / min / IP)

Burst of 30 concurrent requests from one IP:

| Build | Allowed (200/401) | Rejected (HTTP 429) | Verdict |
|-------|------------------:|--------------------:|---------|
| Before fix (§7) | 30 | **0** | ❌ limiter silently fails open |
| **After fix** | 5 | **25** | ✅ exactly matches capacity |

After the fix the bucket key appears in Redis (`tokens` depletes to 0) and the
limiter returns a real HTTP 429. Before the fix it never created the key.

```bash
python benchmarks/loadtest.py ratelimit --n 30
```

---

## 4. Async pipeline decoupling — `POST /api/videos/{id}/analyze`

The endpoint updates status → publishes to RabbitMQ → returns, without waiting for
the ffmpeg → ASR → LLM job.

| Samples (ms) | Median | Min |
|--------------|-------:|----:|
| 46.1, 23.8, 31.3, 30.7, 28.7 | **30.7 ms** | 23.8 ms |

All 5 calls returned `200` (enqueued) in ~31 ms; the rows then transitioned to
`FAILED` **in the background** (no real media/ffmpeg here) — direct evidence that the
request does not block on the pipeline.

```bash
python benchmarks/loadtest.py enqueue --token "$T" --ids 1037,1038,1039,1040,1041
```

---

## 5. Content dedup (MD5) — chunked re-upload

Upload a file, mark it `COMPLETED` (simulating a finished analysis), then re-upload
the byte-identical file.

```json
{
  "first_upload_id": 1044,
  "second_upload_id": 1044,
  "md5_match": true,
  "reused_existing_record": true,
  "rows_sharing_this_md5": 1,
  "duplicate_analysis_avoided": true
}
```

The second upload **reused video id 1044** — no new row, no second ffmpeg+ASR+LLM run.
App log: `Duplicate video detected (md5=…, userId=2), reusing result from videoId=1044`.
A full paid pipeline run avoided per duplicate.

```bash
python benchmarks/dedup_test.py "$TOKEN"
```

---

## 6. Reliability features — verified by inspection (not load-tested)

| Feature | Status | Evidence |
|---------|--------|----------|
| DLQ topology | ✅ live | `video.analysis.v2.queue` carries `x-dead-letter-exchange=video.analysis.dlx`; `video.analysis.dlq` exists; both `durable=true` (`rabbitmqctl list_queues`) |
| Consumer idempotency | ✅ in code | `executeAnalysis`: `if status == COMPLETED → skip` guards against MQ redelivery re-running paid calls |
| WebSocket/STOMP progress | ✅ in code | `pushStep()` pushes `EXTRACTING / TRANSCRIBING / SUMMARIZING` per stage |

Forcing a real MQ redelivery (kill consumer mid-ACK) and an infra-failure DLQ event
were not automated here.

---

## 7. 🐞 Bug found & fixed during benchmarking — rate limiter failed open

**Symptom:** every rate-limited endpoint allowed unlimited requests; the app logged
`Rate limit check failed … allowing through. Cause: Error in execution` on every call.

**Root cause:** `RateLimitAspect` ran the token-bucket Lua script through a
`RedisTemplate` configured with `GenericJackson2JsonRedisSerializer`. That serializer
JSON-encodes the script's `ARGV`, so `5` was sent as `"5"` (with quotes). Inside Lua,
`tonumber('"5"')` returns `nil`, the script throws on arithmetic-with-nil, and the
aspect's `catch` block **fails open**. Confirmed directly:

```
tonumber('"5"')  -> NIL      tonumber('5') -> 5
```

**Fix:** execute the script via `StringRedisTemplate` (String key/value serializers),
so ARGV reaches Lua as raw strings. One-line dependency change in `RateLimitAspect`.
Verified: §3 went from 0/30 rejected to 25/30 rejected.

---

## 8. Not measured here (needs `SILICONFLOW_API_KEY` + a real video)

- Per-stage latency of ffmpeg extraction, ASR transcription, LLM summary.
- The full synchronous-pipeline wall-clock baseline (for an exact "async vs sync" delta).

These call paid external APIs and require real media, so they were left out rather
than estimated.

---

## 9. Résumé-ready phrasings (backed by the numbers above)

- Designed a **Redis cache-aside** layer (TTL jitter, null-sentinel, Redisson
  lock + double-check) that raised hot-read **throughput ~2.7×** and cut **p95 latency
  ~2.1× (p99 ~9.6×)** at 50 concurrent users in local load tests.
- Built a **two-stage async pipeline** (Spring `@Async` + RabbitMQ with DLX/DLQ) so
  the analyze API **returns in ~30 ms** by enqueuing instead of blocking on the
  ffmpeg → ASR → LLM job.
- Added **content-addressed MD5 dedup** with a Redisson hash-keyed lock that **reuses
  prior analysis on identical re-uploads**, avoiding a full paid ASR+LLM run per duplicate.
- Implemented **Lua token-bucket rate limiting**; load-tested a 30-request burst
  against a capacity-5 bucket → **exactly 5 allowed, 25 returned HTTP 429**. *(Found
  and fixed a serializer bug that had been silently disabling the limiter — see §7.)*

_Generated from a live run on 2026-05-25._
