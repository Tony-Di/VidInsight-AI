#!/usr/bin/env python3
"""
VidInsight-AI local load tester (stdlib only, no external deps).

Closed-loop load generator: `concurrency` worker threads each fire requests
back-to-back until `total` requests are done. Reports latency percentiles and
throughput. Used to produce the numbers in BENCHMARKS.md.

Usage:
  python loadtest.py cache-hit   --token T [--conc 50 --total 5000 --id 500]
  python loadtest.py cache-miss  --token T [--conc 50 --total 1000 --id-min 13 --id-max 1013]
  python loadtest.py ratelimit   [--n 30]              # bursts /api/auth/login, counts 429s
  python loadtest.py enqueue     --token T --ids 1,2,3 # POST /analyze latency (needs PENDING rows)
"""
import argparse, json, statistics, subprocess, sys, time, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor

BASE = "http://localhost:8080"


def one_request(method, url, token=None, body=None):
    """Return (status_code, elapsed_seconds). Network errors -> status 0."""
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            resp.read()
            code = resp.status
    except urllib.error.HTTPError as e:
        code = e.code
    except Exception:
        code = 0
    return code, time.perf_counter() - t0


def pct(sorted_vals, p):
    if not sorted_vals:
        return 0.0
    k = min(len(sorted_vals) - 1, int(round((p / 100.0) * (len(sorted_vals) - 1))))
    return sorted_vals[k]


def run_load(make_url, total, conc, token=None, method="GET", body=None):
    """Fire `total` requests across `conc` workers. Returns a stats dict."""
    latencies, codes = [], []
    def task(i):
        return one_request(method, make_url(i), token, body)
    wall0 = time.perf_counter()
    with ThreadPoolExecutor(max_workers=conc) as ex:
        for code, dt in ex.map(task, range(total)):
            codes.append(code)
            latencies.append(dt * 1000.0)  # ms
    wall = time.perf_counter() - wall0
    latencies.sort()
    ok = sum(1 for c in codes if c == 200)
    return {
        "requests": total, "concurrency": conc,
        "ok_200": ok, "non_200": total - ok,
        "wall_seconds": round(wall, 3),
        "throughput_rps": round(total / wall, 1) if wall else 0,
        "p50_ms": round(pct(latencies, 50), 2),
        "p90_ms": round(pct(latencies, 90), 2),
        "p95_ms": round(pct(latencies, 95), 2),
        "p99_ms": round(pct(latencies, 99), 2),
        "max_ms": round(latencies[-1], 2) if latencies else 0,
        "mean_ms": round(statistics.mean(latencies), 2) if latencies else 0,
    }


def redis_flush():
    subprocess.run(["docker", "exec", "vidinsight-redis", "redis-cli", "FLUSHDB"],
                   capture_output=True)


def redis_info_hits():
    out = subprocess.run(["docker", "exec", "vidinsight-redis", "redis-cli", "INFO", "stats"],
                         capture_output=True, text=True).stdout
    hits = misses = 0
    for line in out.splitlines():
        if line.startswith("keyspace_hits:"):
            hits = int(line.split(":")[1])
        elif line.startswith("keyspace_misses:"):
            misses = int(line.split(":")[1])
    total = hits + misses
    return {"keyspace_hits": hits, "keyspace_misses": misses,
            "hit_rate_pct": round(100 * hits / total, 2) if total else None}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mode")
    ap.add_argument("--token", default="")
    ap.add_argument("--conc", type=int, default=50)
    ap.add_argument("--total", type=int, default=5000)
    ap.add_argument("--id", type=int, default=500)
    ap.add_argument("--id-min", type=int, default=13)
    ap.add_argument("--id-max", type=int, default=1013)
    ap.add_argument("--n", type=int, default=30)
    ap.add_argument("--ids", default="")
    args = ap.parse_args()

    if args.mode == "cache-hit":
        # warm the cache, then hammer the same id -> served from Redis
        one_request("GET", f"{BASE}/api/videos/{args.id}", args.token)
        res = run_load(lambda i: f"{BASE}/api/videos/{args.id}", args.total, args.conc, args.token)
        res["scenario"] = "cache-hit (single warmed id)"
        print(json.dumps(res))

    elif args.mode == "cache-miss":
        # flush cache, then hit distinct ids once each -> every read is a miss -> DB
        redis_flush()
        span = args.id_max - args.id_min + 1
        total = min(args.total, span)
        res = run_load(lambda i: f"{BASE}/api/videos/{args.id_min + (i % span)}",
                       total, args.conc, args.token)
        res["scenario"] = "cache-miss (distinct ids, DB read path)"
        print(json.dumps(res))

    elif args.mode == "ratelimit":
        # burst N logins from this IP; bucket capacity=5/min -> expect ~5 pass, rest 429
        codes = []
        with ThreadPoolExecutor(max_workers=args.n) as ex:
            def hit(_):
                return one_request("POST", f"{BASE}/api/auth/login",
                                   body={"email": "bench@test.com", "password": "wrongpass"})[0]
            codes = list(ex.map(hit, range(args.n)))
        summary = {}
        for c in codes:
            summary[str(c)] = summary.get(str(c), 0) + 1
        print(json.dumps({"scenario": "ratelimit /api/auth/login burst",
                          "burst_size": args.n, "status_counts": summary,
                          "rejected_429": summary.get("429", 0)}))

    elif args.mode == "enqueue":
        ids = [int(x) for x in args.ids.split(",") if x]
        lat = []
        for vid in ids:
            code, dt = one_request("POST", f"{BASE}/api/videos/{vid}/analyze", args.token)
            lat.append((vid, code, round(dt * 1000, 2)))
        print(json.dumps({"scenario": "POST /{id}/analyze enqueue latency",
                          "samples": lat,
                          "median_ms": round(statistics.median([l[2] for l in lat]), 2) if lat else 0}))

    elif args.mode == "redis-stats":
        print(json.dumps(redis_info_hits()))

    else:
        print("unknown mode", file=sys.stderr); sys.exit(1)


if __name__ == "__main__":
    main()
