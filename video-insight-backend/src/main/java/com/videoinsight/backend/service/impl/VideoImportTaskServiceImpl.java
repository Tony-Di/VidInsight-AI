package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.enums.VideoStatus;
import com.videoinsight.backend.mapper.VideoInfoMapper;
import com.videoinsight.backend.service.FileStorageService;
import com.videoinsight.backend.service.VideoCacheService;
import com.videoinsight.backend.service.VideoDownloadService;
import com.videoinsight.backend.service.VideoImportTaskService;
import com.videoinsight.backend.util.FileHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoImportTaskServiceImpl implements VideoImportTaskService {

    private final VideoInfoMapper videoInfoMapper;

    private final VideoDownloadService videoDownloadService;

    private final FileStorageService fileStorageService;

    private final VideoCacheService videoCacheService;

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

            // MD5 去重：下载完成后算哈希，命中已完成记录则复用其 ASR + AI 结果
            String md5 = computeMd5OrNull(downloadedFile);
            if (md5 != null) {
                VideoInfo existing = videoInfoMapper.findCompletedByMd5(md5);
                if (existing != null) {
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
                    return;
                }
            }

            String localSourceUrl = fileStorageService.saveVideo(downloadedFile, downloadedFile.getFileName().toString());

            videoInfo.setSourceUrl(localSourceUrl);
            videoInfo.setFileMd5(md5);
            videoInfo.setVideoStatus(VideoStatus.PENDING);
            videoInfo.setSummary(null);
            videoInfo.setUpdatedAt(LocalDateTime.now());
            videoInfoMapper.updateById(videoInfo);
            videoCacheService.evictDetail(videoInfo.getId());
        } catch (Exception exception) {
            log.error("Video import failed, videoId={}", videoId, exception);
            videoInfo.setVideoStatus(VideoStatus.IMPORT_FAILED);
            videoInfo.setSummary(getRootCauseMessage(exception));
            videoInfo.setUpdatedAt(LocalDateTime.now());
            videoInfoMapper.updateById(videoInfo);
            videoCacheService.evictDetail(videoInfo.getId());
        } finally {
            deleteTempFile(downloadedFile);
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
            Files.deleteIfExists(downloadedFile);
            Path parent = downloadedFile.getParent();
            if (parent != null) {
                Files.deleteIfExists(parent);
            }
        } catch (IOException exception) {
            log.warn("Failed to delete imported temp file {}", downloadedFile, exception);
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
