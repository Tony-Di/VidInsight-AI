package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.config.YtDlpProperties;
import com.videoinsight.backend.service.VideoDownloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class YtDlpVideoDownloadServiceImpl implements VideoDownloadService {

    private final YtDlpProperties ytDlpProperties;

    @Override
    public Path download(String sourceUrl) {
        if (!StringUtils.hasText(ytDlpProperties.getPath())) {
            throw new IllegalStateException("app.ytdlp.path is not configured");
        }
        if (!StringUtils.hasText(sourceUrl)
                || (!sourceUrl.startsWith("http://") && !sourceUrl.startsWith("https://"))) {
            throw new IllegalArgumentException("sourceUrl must be an http or https URL");
        }

        try {
            Path tempDir = Files.createTempDirectory("video-import-").toAbsolutePath().normalize();
            Path tempFile = tempDir.resolve(UUID.randomUUID() + ".mp4").normalize();
            Path logFile = tempDir.resolve("yt-dlp.log").normalize();

            List<String> command = buildCommand(sourceUrl, tempFile);
            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile());
            // Force yt-dlp (Python) to emit UTF-8 regardless of the host's ANSI code page,
            // so the log we read back is decodable. Without this, Chinese-Windows hosts
            // write GBK bytes that crash Files.readString as MalformedInputException.
            processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
            Process process = processBuilder.start();

            boolean finished = process.waitFor(ytDlpProperties.getDownloadTimeoutMinutes(), TimeUnit.MINUTES);
            // Use new String(bytes, UTF_8) instead of Files.readString — the former uses
            // CodingErrorAction.REPLACE, so any stray non-UTF-8 bytes degrade to U+FFFD
            // rather than aborting an otherwise-successful download.
            String logs = Files.exists(logFile)
                    ? new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8)
                    : "";
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("yt-dlp download timed out after "
                        + ytDlpProperties.getDownloadTimeoutMinutes() + " minutes");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("yt-dlp download failed: " + logs);
            }
            if (!Files.exists(tempFile)) {
                throw new IllegalStateException("yt-dlp finished but output file was not created");
            }
            return tempFile;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to start yt-dlp: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("yt-dlp download was interrupted", exception);
        }
    }

    private List<String> buildCommand(String sourceUrl, Path outputPath) {
        List<String> command = new ArrayList<>();
        command.add(ytDlpProperties.getPath());
        command.add("--no-playlist");
        command.add("--no-check-certificate");
        command.add("--merge-output-format");
        command.add("mp4");
        command.add("-f");
        command.add("bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/bestvideo[height<=720]+bestaudio/best[height<=720]");
        command.add("--user-agent");
        command.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        command.add("-o");
        command.add(outputPath.toString());
        if (StringUtils.hasText(ytDlpProperties.getFfmpegLocation())) {
            command.add("--ffmpeg-location");
            command.add(ytDlpProperties.getFfmpegLocation());
        }
        command.add(sourceUrl);
        return command;
    }
}
