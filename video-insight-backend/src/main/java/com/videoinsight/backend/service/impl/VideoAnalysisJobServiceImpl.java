package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.enums.VideoStatus;
import com.videoinsight.backend.mapper.VideoInfoMapper;
import com.videoinsight.backend.model.response.VideoAnalysisResult;
import com.videoinsight.backend.service.AiSummaryService;
import com.videoinsight.backend.service.FileStorageService;
import com.videoinsight.backend.service.MediaProcessingService;
import com.videoinsight.backend.service.SpeechRecognitionService;
import com.videoinsight.backend.service.VideoAnalysisJobService;
import com.videoinsight.backend.service.VideoCacheService;
import com.videoinsight.backend.websocket.VideoStatusPush;
import com.videoinsight.backend.websocket.VideoStatusPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoAnalysisJobServiceImpl implements VideoAnalysisJobService {

    private final VideoInfoMapper videoInfoMapper;

    private final MediaProcessingService mediaProcessingService;

    private final SpeechRecognitionService speechRecognitionService;

    private final AiSummaryService aiSummaryService;

    private final VideoCacheService videoCacheService;

    private final VideoStatusPushService videoStatusPushService;

    private final FileStorageService fileStorageService;

    private final RedissonClient redissonClient;

    private static final String ANALYSIS_LOCK_PREFIX = "vidinsight:lock:analysis:";

    @Override
    public void executeAnalysis(Long videoId) {
        RLock lock = redissonClient.getLock(ANALYSIS_LOCK_PREFIX + videoId);
        // waitTime=0：抢不到说明已有 worker 在处理本视频，直接跳过（正常返回 → ACK，消息不重投）。
        // 不传 leaseTime → Redisson WatchDog 自动续租（默认锁 30s，每 10s 续一次），跑多久续多久；
        // worker 进程崩溃则 WatchDog 停止续租，锁最多 30s 后过期，重投的消息可被另一个 worker 重新领取恢复。
        if (!lock.tryLock()) {
            log.info("Video {} 正被其他 worker 处理，跳过本次 MQ 投递。", videoId);
            return;
        }

        try {
            VideoInfo videoInfo = videoInfoMapper.selectById(videoId);
            if (videoInfo == null) {
                log.warn("Video analysis skipped because video {} does not exist.", videoId);
                return;
            }

            // 幂等保护：已完成的任务不重复执行（防止 MQ 重投导致重复分析）
            if (videoInfo.getVideoStatus() == VideoStatus.COMPLETED) {
                log.info("Video {} already completed, skipping duplicate MQ delivery.", videoId);
                return;
            }

            try {
                pushStep(videoInfo, "EXTRACTING");
                String audioUrl = mediaProcessingService.extractAudio(videoInfo);
                videoInfo.setAudioUrl(audioUrl);

                pushStep(videoInfo, "TRANSCRIBING");
                String transcript = speechRecognitionService.transcribe(audioUrl);
                videoInfo.setTranscript(transcript);

                pushStep(videoInfo, "SUMMARIZING");
                String summary = aiSummaryService.summarize(transcript);

                videoInfo.setVideoStatus(VideoStatus.COMPLETED);
                videoInfo.setSummary(summary);
                videoInfo.setUpdatedAt(LocalDateTime.now());
                videoInfoMapper.updateById(videoInfo);
                videoCacheService.evictDetail(videoId);
                videoCacheService.evictUserLists(videoInfo.getUserId());
                videoStatusPushService.push(videoInfo.getUserId(),
                        new VideoStatusPush(videoId, VideoStatus.COMPLETED.name(),
                                fileStorageService.publicUrl(audioUrl), null));
            } catch (Exception exception) {
                log.error("Video analysis failed, videoId={}", videoId, exception);
                videoInfo.setVideoStatus(VideoStatus.FAILED);
                videoInfo.setSummary(getRootCauseMessage(exception));
                videoInfo.setUpdatedAt(LocalDateTime.now());
                videoInfoMapper.updateById(videoInfo);
                videoCacheService.evictDetail(videoId);
                videoCacheService.evictUserLists(videoInfo.getUserId());
                videoStatusPushService.push(videoInfo.getUserId(),
                        new VideoStatusPush(videoId, VideoStatus.FAILED.name(), null, null));
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void pushStep(VideoInfo videoInfo, String step) {
        videoStatusPushService.push(videoInfo.getUserId(),
                new VideoStatusPush(videoInfo.getId(), VideoStatus.PROCESSING.name(), null, step));
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
