package com.videoinsight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videoinsight.backend.entity.VideoAgentContext;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.mapper.VideoAgentContextMapper;
import com.videoinsight.backend.model.agent.VideoContextData;
import com.videoinsight.backend.service.FileStorageService;
import com.videoinsight.backend.service.LocalAccess;
import com.videoinsight.backend.service.MediaProcessingService;
import com.videoinsight.backend.service.OcrService;
import com.videoinsight.backend.service.SpeechRecognitionService;
import com.videoinsight.backend.service.VideoAgentContextService;
import com.videoinsight.backend.util.ImageHashUtil;
import com.videoinsight.backend.util.VideoSegmentMerger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

@Slf4j
@Service
public class VideoAgentContextServiceImpl implements VideoAgentContextService {

    private static final int DUP_HAMMING_THRESHOLD = 5;

    private static final long BUILD_TIMEOUT_MINUTES = 60;

    private final VideoAgentContextMapper contextMapper;
    private final FileStorageService fileStorageService;
    private final MediaProcessingService mediaProcessingService;
    private final SpeechRecognitionService speechRecognitionService;
    private final OcrService ocrService;
    private final ObjectMapper objectMapper;
    private final Executor agentBranchExecutor;

    public VideoAgentContextServiceImpl(VideoAgentContextMapper contextMapper,
                                        FileStorageService fileStorageService,
                                        MediaProcessingService mediaProcessingService,
                                        SpeechRecognitionService speechRecognitionService,
                                        OcrService ocrService,
                                        ObjectMapper objectMapper,
                                        @Qualifier("agentBranchExecutor") Executor agentBranchExecutor) {
        this.contextMapper = contextMapper;
        this.fileStorageService = fileStorageService;
        this.mediaProcessingService = mediaProcessingService;
        this.speechRecognitionService = speechRecognitionService;
        this.ocrService = ocrService;
        this.objectMapper = objectMapper;
        this.agentBranchExecutor = agentBranchExecutor;
    }

    @Override
    public VideoContextData getOrBuild(VideoInfo videoInfo) {
        VideoAgentContext cached = contextMapper.selectOne(new LambdaQueryWrapper<VideoAgentContext>()
                .eq(VideoAgentContext::getVideoId, videoInfo.getId()));
        if (cached != null) {
            try {
                return objectMapper.readValue(cached.getContextJson(), VideoContextData.class);
            } catch (JsonProcessingException exception) {
                log.warn("context_json deserialize failed, rebuilding. videoId={}", videoInfo.getId(), exception);
            }
        }
        VideoContextData context = build(videoInfo);
        persist(videoInfo.getId(), cached, context);
        return context;
    }

    private VideoContextData build(VideoInfo videoInfo) {
        Path workDir;
        try {
            workDir = Files.createTempDirectory("vid-agent-ctx-");
        } catch (IOException exception) {
            throw new IllegalStateException("failed to create agent work dir", exception);
        }
        // accessLocal 只做一次:MinIO 场景下视频只下载一份,双分支共享同一本地文件
        try (LocalAccess access = fileStorageService.accessLocal(videoInfo.getSourceUrl())) {
            Path videoPath = access.path();
            CompletableFuture<Branch<VideoSegmentMerger.TranscriptPart>> asrFuture =
                    submit(() -> transcribeSegments(videoPath, workDir.resolve("audio")));
            CompletableFuture<Branch<VideoSegmentMerger.FramePart>> ocrFuture =
                    submit(() -> recognizeKeyFrames(videoPath, workDir.resolve("frames")));
            try {
                CompletableFuture.allOf(asrFuture, ocrFuture).get(BUILD_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            } catch (TimeoutException exception) {
                asrFuture.cancel(true);
                ocrFuture.cancel(true);
                throw new IllegalStateException("VideoContext build exceeded time budget", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("VideoContext build interrupted", exception);
            } catch (ExecutionException exception) {
                throw new IllegalStateException("VideoContext branch crashed", exception.getCause());
            }
            Branch<VideoSegmentMerger.TranscriptPart> asr = asrFuture.join();
            Branch<VideoSegmentMerger.FramePart> ocr = ocrFuture.join();
            if (asr.failed() && ocr.failed()) {
                IllegalStateException failure = new IllegalStateException("ASR 与 OCR 分支均失败", asr.error());
                failure.addSuppressed(ocr.error());
                throw failure;
            }
            if (asr.failed()) {
                log.warn("agent_context_asr_branch_failed videoId={}", videoInfo.getId(), asr.error());
            }
            if (ocr.failed()) {
                log.warn("agent_context_ocr_branch_failed videoId={}", videoInfo.getId(), ocr.error());
            }
            List<VideoContextData.VideoSegment> segments = VideoSegmentMerger.merge(asr.items(), ocr.items());
            if (segments.isEmpty()) {
                throw new IllegalStateException("视频未解析出有效语音或画面文字");
            }
            return new VideoContextData(videoInfo.getSourceUrl(), segments);
        } finally {
            deleteRecursively(workDir);
        }
    }

    private List<VideoSegmentMerger.TranscriptPart> transcribeSegments(Path videoPath, Path audioDir) {
        List<Path> audioFiles = mediaProcessingService.extractAudioSegments(videoPath, audioDir);
        List<VideoSegmentMerger.TranscriptPart> result = new ArrayList<>();
        int failed = 0;
        for (int i = 0; i < audioFiles.size(); i++) {
            try {
                String text = speechRecognitionService.transcribeFile(audioFiles.get(i));
                if (text != null && !text.isBlank()) {
                    result.add(new VideoSegmentMerger.TranscriptPart(
                            i * VideoSegmentMerger.WINDOW_MS, (i + 1) * VideoSegmentMerger.WINDOW_MS, text));
                }
            } catch (RuntimeException exception) {
                failed++;
                log.warn("asr_segment_failed index={} file={}", i, audioFiles.get(i).getFileName(), exception);
            }
        }
        if (result.isEmpty() && failed > 0) {
            throw new IllegalStateException("所有 ASR 分片均失败");
        }
        return result;
    }

    private List<VideoSegmentMerger.FramePart> recognizeKeyFrames(Path videoPath, Path frameDir) {
        List<MediaProcessingService.KeyFrame> keyFrames =
                mediaProcessingService.extractKeyFrames(videoPath, frameDir);
        List<VideoSegmentMerger.FramePart> result = new ArrayList<>();
        Long previousHash = null;
        int failed = 0;
        for (MediaProcessingService.KeyFrame frame : keyFrames) {
            long hash;
            try {
                hash = ImageHashUtil.differenceHash(frame.imagePath().toFile());
            } catch (IOException exception) {
                failed++;
                continue;
            }
            if (previousHash != null && ImageHashUtil.hammingDistance(previousHash, hash) <= DUP_HAMMING_THRESHOLD) {
                continue; // 画面几乎没变,跳过重复 OCR
            }
            previousHash = hash;
            try {
                String ocrText = ocrService.recognize(frame.imagePath());
                result.add(new VideoSegmentMerger.FramePart(
                        frame.timestampMs(), ocrText, String.valueOf(frame.timestampMs())));
            } catch (RuntimeException exception) {
                failed++;
                log.warn("ocr_frame_failed timestampMs={}", frame.timestampMs(), exception);
            }
        }
        if (result.isEmpty() && failed > 0) {
            throw new IllegalStateException("所有 OCR 关键帧均失败");
        }
        return result;
    }

    private void persist(Long videoId, VideoAgentContext existing, VideoContextData context) {
        try {
            String json = objectMapper.writeValueAsString(context);
            if (existing == null) {
                VideoAgentContext row = new VideoAgentContext();
                row.setVideoId(videoId);
                row.setContextJson(json);
                row.setCreatedAt(LocalDateTime.now());
                row.setUpdatedAt(LocalDateTime.now());
                contextMapper.insert(row);
            } else {
                existing.setContextJson(json);
                existing.setChunksJson(null); // 上下文重建后旧检索索引作废
                existing.setUpdatedAt(LocalDateTime.now());
                contextMapper.updateById(existing);
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize VideoContextData", exception);
        }
    }

    private <T> CompletableFuture<Branch<T>> submit(ThrowingSupplier<List<T>> work) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Branch.ok(work.get());
            } catch (Exception exception) {
                return Branch.<T>fail(exception);
            }
        }, agentBranchExecutor);
    }

    private void deleteRecursively(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException exception) {
            log.warn("agent_workdir_cleanup_failed dir={}", directory, exception);
        }
    }

    private record Branch<T>(List<T> items, Exception error) {
        private static <T> Branch<T> ok(List<T> items) {
            return new Branch<>(items, null);
        }

        private static <T> Branch<T> fail(Exception error) {
            return new Branch<>(List.of(), error);
        }

        private boolean failed() {
            return error != null;
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
