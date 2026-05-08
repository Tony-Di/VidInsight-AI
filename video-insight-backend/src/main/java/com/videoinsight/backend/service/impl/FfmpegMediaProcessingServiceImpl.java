package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.service.FileStorageService;
import com.videoinsight.backend.service.MediaProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class FfmpegMediaProcessingServiceImpl implements MediaProcessingService {

    private final FileStorageService fileStorageService;

    @Value("${app.upload.audio-dir}")
    private String audioDir;

    @Value("${app.ffmpeg.path}")
    private String ffmpegPath;

    @Override
    public String extractAudio(VideoInfo videoInfo) {
        Path videoPath = fileStorageService.resolveLocalPath(videoInfo.getSourceUrl());
        String audioFilename = videoInfo.getId() + ".mp3";
        Path audioRootPath = Path.of(audioDir).toAbsolutePath().normalize();
        Path audioPath = audioRootPath.resolve(audioFilename).normalize();

        if (!audioPath.startsWith(audioRootPath)) {
            throw new IllegalArgumentException("invalid audio output path");
        }

        try {
            Files.createDirectories(audioRootPath);
            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegPath,
                    "-y",
                    "-i", videoPath.toString(),
                    "-vn",
                    "-ar", "16000",
                    "-ac", "1",
                    "-b:a", "64k",
                    audioPath.toString()
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
            return "/uploads/audio/" + audioFilename;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to run ffmpeg", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ffmpeg was interrupted", exception);
        }
    }
}
