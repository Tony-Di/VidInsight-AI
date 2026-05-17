package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.enums.VideoStatus;
import com.videoinsight.backend.mapper.VideoInfoMapper;
import com.videoinsight.backend.service.FileStorageService;
import com.videoinsight.backend.service.VideoCacheService;
import com.videoinsight.backend.service.VideoDownloadService;
import com.videoinsight.backend.service.VideoImportTaskService;
import com.videoinsight.backend.util.FileHashUtil;
import com.videoinsight.backend.websocket.VideoStatusPush;
import com.videoinsight.backend.websocket.VideoStatusPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoImportTaskServiceImpl implements VideoImportTaskService {

    private final VideoInfoMapper videoInfoMapper;

    private final VideoDownloadService videoDownloadService;

    private static final String MD5_LOCK_PREFIX = "vidinsight:lock:upload:md5:";
    private static final long MD5_LOCK_WAIT_SECONDS = 10;

    private final FileStorageService fileStorageService;

    private final VideoCacheService videoCacheService;

    private final RedissonClient redissonClient;

    private final VideoStatusPushService videoStatusPushService;

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

            // MD5 去重:下载完成后算哈希,命中已完成记录则复用其 ASR + AI 结果。
            // Redisson 锁串行化"查 + 决策",防止两个相同 URL 并发导入时各跑一遍 ASR/LLM。
            String md5 = computeMd5OrNull(downloadedFile);
            if (md5 != null && reuseIfDuplicate(videoInfo, md5)) {
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
                    new VideoStatusPush(videoId, VideoStatus.PENDING.name(), null, null));
        } catch (Exception exception) {
            log.error("Video import failed, videoId={}", videoId, exception);
            videoInfo.setVideoStatus(VideoStatus.IMPORT_FAILED);
            videoInfo.setSummary(getRootCauseMessage(exception));
            videoInfo.setUpdatedAt(LocalDateTime.now());
            videoInfoMapper.updateById(videoInfo);
            videoCacheService.evictDetail(videoInfo.getId());
            videoCacheService.evictUserLists(videoInfo.getUserId());
            videoStatusPushService.push(videoInfo.getUserId(),
                    new VideoStatusPush(videoId, VideoStatus.IMPORT_FAILED.name(), null, null));
        } finally {
            deleteTempFile(downloadedFile);
        }
    }

    /**
     * 锁内执行:查 COMPLETED 同 MD5 的记录,命中就把当前 video 写成 COMPLETED 复用其结果。
     * @return true 表示已复用并写库,调用方应直接 return
     */
    private boolean reuseIfDuplicate(VideoInfo videoInfo, String md5) {
        RLock lock = redissonClient.getLock(MD5_LOCK_PREFIX + md5);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(MD5_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("MD5 dedup lock busy for md5={}, skipping reuse check and proceeding to PENDING", md5);
                return false;
            }
            // MD5 复用按用户限定:不复用别人的已分析结果(隐私 + 所有权)。
            VideoInfo existing = videoInfoMapper.findCompletedByMd5AndUser(md5, videoInfo.getUserId());
            if (existing == null) {
                return false;
            }
            log.info("Duplicate video detected on URL import (md5={}), reusing result from videoId={}",
                    md5, existing.getId());
            videoInfo.setFileMd5(md5);
            videoInfo.setSourceUrl(existing.getSourceUrl());
            videoInfo.setAudioUrl(existing.getAudioUrl());
            videoInfo.setTranscript(existing.getTranscript());
            videoInfo.setSummary(existing.getSummary());
            videoInfo.setVideoStatus(VideoStatus.COMPLETED);
            videoInfo.setUpdatedAt(LocalDateTime.now());
            videoInfoMapper.updateById(videoInfo);
            videoCacheService.evictDetail(videoInfo.getId());
            videoCacheService.evictUserLists(videoInfo.getUserId());
            videoStatusPushService.push(videoInfo.getUserId(),
                    new VideoStatusPush(videoInfo.getId(), VideoStatus.COMPLETED.name(), videoInfo.getAudioUrl(), null));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for MD5 dedup lock", e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String computeMd5OrNull(Path path) {
        try {
            return FileHashUtil.md5(path);
        } catch (IOException e) {
            log.warn("Failed to compute MD5 for {}: {}", path, e.getMessage());
            return null;
        }
    }

    private void deleteTempFile(Path downloadedFile) {
        if (downloadedFile == null) {
            return;
        }
        try {
            Path dir = downloadedFile.getParent();
            Path root = dir != null ? dir : downloadedFile;
            try (var stream = Files.walk(root)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
            }
        } catch (IOException exception) {
            log.warn("Failed to delete imported temp dir {}", downloadedFile, exception);
        }
    }

    private String getRootCauseMessage(Exception exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        if (rootCause.getMessage() == null || rootCause.getMessage().isBlank()) {
            return exception.getMessage();
        }
        if (rootCause == exception) {
            return rootCause.getMessage();
        }
        return exception.getMessage() + ": " + rootCause.getMessage();
    }
}
