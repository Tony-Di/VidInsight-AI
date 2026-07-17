package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.service.FileStorageService;
import com.videoinsight.backend.service.LocalAccess;
import com.videoinsight.backend.service.MediaProcessingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class FfmpegMediaProcessingServiceImpl implements MediaProcessingService {

    private static final Pattern PTS_TIME = Pattern.compile("pts_time:([0-9.]+)");

    private static final long FALLBACK_FRAME_INTERVAL_MS = 30_000L;

    private final FileStorageService fileStorageService;

    private final String ffmpegPath;

    public FfmpegMediaProcessingServiceImpl(FileStorageService fileStorageService,
                                            @Value("${app.ffmpeg.path}") String ffmpegPath) {
        this.fileStorageService = fileStorageService;
        this.ffmpegPath = ffmpegPath;
    }

    @Override
    public String extractAudio(VideoInfo videoInfo) {
        String audioFilename = videoInfo.getId() + ".mp3";
        Path tempAudio;
        try {
            tempAudio = Files.createTempFile("vid-audio-", ".mp3");
        } catch (IOException e) {
            throw new IllegalStateException("failed to create temp audio file", e);
        }

        try (LocalAccess access = fileStorageService.accessLocal(videoInfo.getSourceUrl())) {
            Path videoPath = access.path();
            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegPath,
                    "-y",
                    "-i", videoPath.toString(),
                    "-vn",
                    "-ar", "16000",
                    "-ac", "1",
                    "-b:a", "64k",
                    tempAudio.toString()
            );
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

            Process process = processBuilder.start();
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("ffmpeg timed out");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("ffmpeg failed with exit code " + process.exitValue());
            }
            return fileStorageService.saveAudio(tempAudio, audioFilename);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to run ffmpeg", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ffmpeg was interrupted", exception);
        } finally {
            try { Files.deleteIfExists(tempAudio); } catch (IOException ignored) {}
        }
    }

    @Override
    public List<Path> extractAudioSegments(Path videoFile, Path workDir) {
        try {
            Files.createDirectories(workDir);
            Path outputPattern = workDir.resolve("audio_%03d.mp3");
            runFfmpeg(List.of(
                    ffmpegPath, "-y", "-i", videoFile.toString(),
                    "-vn", "-ar", "16000", "-ac", "1", "-b:a", "64k",
                    "-f", "segment", "-segment_time", "60", "-reset_timestamps", "1",
                    outputPattern.toString()
            ), null, Duration.ofMinutes(15));
            try (Stream<Path> paths = Files.list(workDir)) {
                return paths.filter(p -> p.getFileName().toString().startsWith("audio_"))
                        .sorted().toList();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to split audio segments", exception);
        }
    }

    @Override
    public List<KeyFrame> extractKeyFrames(Path videoFile, Path workDir) {
        try {
            Files.createDirectories(workDir);
            List<Long> timestamps = new ArrayList<>();
            runFfmpeg(List.of(
                    ffmpegPath, "-y", "-i", videoFile.toString(),
                    "-vf", "select=eq(n\\,0)+gt(scene\\,0.35)+gte(t-prev_selected_t\\,30),showinfo",
                    "-vsync", "vfr",
                    workDir.resolve("frame_%06d.jpg").toString()
            ), timestamps, Duration.ofMinutes(15));
            List<Path> frameFiles;
            try (Stream<Path> paths = Files.list(workDir)) {
                frameFiles = paths.filter(p -> p.getFileName().toString().startsWith("frame_"))
                        .sorted().toList();
            }
            List<KeyFrame> result = new ArrayList<>();
            for (int i = 0; i < frameFiles.size(); i++) {
                long timestampMs = i < timestamps.size()
                        ? timestamps.get(i)
                        : i * FALLBACK_FRAME_INTERVAL_MS;
                result.add(new KeyFrame(frameFiles.get(i), timestampMs));
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to extract key frames", exception);
        }
    }

    /**
     * ffmpeg 输出重定向到临时日志文件;ptsTimestampsMs 非空时从 showinfo 行解析每个
     * 被选中帧的 pts_time(秒 → 毫秒),顺序与输出的 frame_%06d.jpg 一一对应。
     */
    private void runFfmpeg(List<String> command, List<Long> ptsTimestampsMs, Duration timeout)
            throws IOException {
        Path logFile = Files.createTempFile("vid-ffmpeg-", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();
            if (!process.waitFor(timeout.toMinutes(), TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IllegalStateException("ffmpeg timed out");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("ffmpeg failed with exit code " + process.exitValue());
            }
            if (ptsTimestampsMs != null) {
                // ISO_8859_1:ffmpeg 日志可能混入非 UTF-8 字节,避免 Files.lines 抛 MalformedInput
                try (Stream<String> lines = Files.lines(logFile, StandardCharsets.ISO_8859_1)) {
                    lines.filter(line -> line.contains("pts_time:")).forEach(line -> {
                        Matcher matcher = PTS_TIME.matcher(line);
                        if (matcher.find()) {
                            ptsTimestampsMs.add((long) (Double.parseDouble(matcher.group(1)) * 1000));
                        }
                    });
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ffmpeg was interrupted", exception);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            try { Files.deleteIfExists(logFile); } catch (IOException ignored) {}
        }
    }
}
