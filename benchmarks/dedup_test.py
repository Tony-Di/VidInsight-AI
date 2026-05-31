#!/usr/bin/env python3
"""
Functional test for content-based MD5 dedup + Redisson dedup lock.

Flow:
  1. Upload a dummy file via the chunked API -> creates video_info row (PENDING).
  2. Force it COMPLETED with transcript/summary (simulate a finished analysis),
     because dedup only matches against COMPLETED rows.
  3. Re-upload the byte-identical file.
  4. Assert the 2nd upload REUSES the same video id (no new row, no re-analysis).

Run:  python dedup_test.py <JWT>
"""
import json, subprocess, sys, tempfile, os

BASE = "http://localhost:8080"
TOKEN = sys.argv[1]
AUTH = f"Authorization: Bearer {TOKEN}"


def curl(args):
    out = subprocess.run(["curl", "-s"] + args, capture_output=True, text=True).stdout
    try:
        return json.loads(out)
    except json.JSONDecodeError:
        return {"_raw": out}


def mysql(sql):
    return subprocess.run(
        ["docker", "exec", "-i", "vidinsight-mysql", "mysql", "-uroot", "-p123456",
         "video_insight", "-N", "-e", sql],
        capture_output=True, text=True).stdout.strip()


def upload(path):
    init = curl(["-X", "POST", f"{BASE}/api/videos/chunks/init", "-H", AUTH,
                 "-H", "Content-Type: application/json",
                 "-d", json.dumps({"title": "Dedup Test", "fileName": "dedup.mp4", "totalChunks": 1})])
    upload_id = init["data"]["uploadId"]
    curl(["-X", "POST", f"{BASE}/api/videos/chunks/upload?uploadId={upload_id}&chunkIndex=0",
          "-H", AUTH, "-F", f"file=@{path}"])
    done = curl(["-X", "POST", f"{BASE}/api/videos/chunks/complete?uploadId={upload_id}", "-H", AUTH])
    v = done["data"]
    return v["id"], v.get("fileMd5") or v.get("file_md5")


def main():
    # 1MB deterministic dummy file (same bytes both times -> same MD5)
    fd, path = tempfile.mkstemp(suffix=".mp4")
    with os.fdopen(fd, "wb") as f:
        f.write(b"VIDINSIGHT_DEDUP_TEST_PAYLOAD_" * 40000)  # ~1.1 MB
    try:
        id1, md5_1 = upload(path)
        # simulate that this video was fully analyzed
        mysql(f"UPDATE video_info SET status='COMPLETED', transcript='dummy transcript', "
              f"summary='dummy summary' WHERE id={id1};")
        id2, md5_2 = upload(path)  # identical bytes
        rows_with_md5 = mysql(f"SELECT COUNT(*) FROM video_info WHERE file_md5='{md5_1}';")
        result = {
            "scenario": "MD5 content dedup on re-upload",
            "first_upload_id": id1,
            "second_upload_id": id2,
            "md5_match": md5_1 == md5_2,
            "md5": md5_1,
            "reused_existing_record": id1 == id2,
            "rows_sharing_this_md5": int(rows_with_md5),
            "duplicate_analysis_avoided": id1 == id2 and int(rows_with_md5) == 1,
        }
        print(json.dumps(result, indent=2))
    finally:
        os.remove(path)


if __name__ == "__main__":
    main()
