package com.videoinsight.backend.service;

import com.videoinsight.backend.entity.VideoInfo;

import java.nio.file.Path;
import java.util.List;

public interface MediaProcessingService {

    String extractAudio(VideoInfo videoInfo);

    /**
     * 把整条音轨切成 60 秒 mp3 分片(16kHz 单声道),写入 workDir,按文件名排序返回。
     * 分片序号 i 对应视频 [i*60s, (i+1)*60s) 区间——这是 Agent 时间戳证据的来源。
     */
    List<Path> extractAudioSegments(Path videoFile, Path workDir);

    /**
     * 关键帧抽取:场景变化 >0.35 或距上一帧 ≥30s 或第 0 帧。
     * timestampMs 解析自 ffmpeg showinfo 的 pts_time。
     */
    List<KeyFrame> extractKeyFrames(Path videoFile, Path workDir);

    record KeyFrame(Path imagePath, long timestampMs) {
    }
}
