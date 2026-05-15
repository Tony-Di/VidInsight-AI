-- Token bucket rate limiter.
-- Redis 单线程执行整段 Lua,保证"读 → 算 → 写"原子,
-- 等价于把 read-modify-write 包到一个事务里,但比 MULTI/EXEC 灵活——可以中途 if/else。
--
-- KEYS[1] = 桶的 key,例如 "vidinsight:ratelimit:video.import:user:42"
-- ARGV[1] = capacity            桶容量(允许的突发量)
-- ARGV[2] = refill_per_second   每秒补几个令牌(小数)
-- ARGV[3] = now_ms              客户端当前时间(毫秒)
--
-- 返回值: 1 = 放行, 0 = 拒绝
-- 设计点:
--   - 不用定时器后台补令牌,改成"懒补"——每次进来按"距上次访问过了多少秒"算应该补多少。
--   - tokens 用浮点表示,允许小数(否则 5/min = 0.083/s 永远凑不齐 1 个)。
--   - EXPIRE 120s:桶 2 分钟没人用就让 Redis 回收 key,避免百万僵尸 key 堆积。
--     注意 TTL 要 >= 补满整个桶的时间,否则用户偶尔来一次反而被"重置成空桶"。

local capacity = tonumber(ARGV[1])
local rate     = tonumber(ARGV[2])
local now      = tonumber(ARGV[3])

local data   = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(data[1])
local ts     = tonumber(data[2])

if tokens == nil then
    tokens = capacity
    ts = now
end

-- 懒补:距上次访问过了多少秒,就补多少令牌(不超过桶容量)
local elapsed_seconds = math.max(0, now - ts) / 1000
tokens = math.min(capacity, tokens + elapsed_seconds * rate)

if tokens >= 1 then
    tokens = tokens - 1
    redis.call('HMSET', KEYS[1], 'tokens', tokens, 'ts', now)
    redis.call('EXPIRE', KEYS[1], 120)
    return 1
else
    -- 拒绝也要写回 ts,否则下次客户端时间戳还能从更早的位置开始算补令牌(送送送)
    redis.call('HMSET', KEYS[1], 'tokens', tokens, 'ts', now)
    redis.call('EXPIRE', KEYS[1], 120)
    return 0
end
