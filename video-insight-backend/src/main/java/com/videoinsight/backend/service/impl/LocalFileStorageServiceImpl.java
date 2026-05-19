package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.service.FileStorageService;
import com.videoinsight.backend.service.LocalAccess;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageServiceImpl implements FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".mp4", ".mov", ".avi", ".mkv", ".webm");
    private static final String VIDEO_URL_PREFIX = "/uploads/videos/";
    private static final String AUDIO_URL_PREFIX = "/uploads/audio/";

    @Value("${app.upload.video-dir}")
    private String videoDir;

    @Value("${app.upload.chunk-dir}")
    private String chunkDir;

    @Value("${app.upload.audio-dir}")
    private String audioDir;

    @Override
    public String saveVideo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("video file is required");
        }

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "video" : file.getOriginalFilename()
        );
        String extension = getExtension(originalFilename);
        validateExtension(extension);

        String storedFilename = UUID.randomUUID() + extension;
        Path uploadPath = Path.of(videoDir).toAbsolutePath().normalize();
        Path targetPath = uploadPath.resolve(storedFilename).normalize();

        if (!targetPath.startsWith(uploadPath)) {
            throw new IllegalArgumentException("invalid file path");
        }

        try {
            Files.createDirectories(uploadPath);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to save video file", exception);
        }

        return VIDEO_URL_PREFIX + storedFilename;
    }

    @Override
    public String saveVideo(Path file, String originalFilename) {
        if (file == null || !Files.exists(file)) {
            throw new IllegalArgumentException("video file is required");
        }

        String filename = StringUtils.cleanPath(
                StringUtils.hasText(originalFilename) ? originalFilename : file.getFileName().toString()
        );
        String extension = getExtension(filename);
        validateExtension(extension);

        String storedFilename = UUID.randomUUID() + extension;
        Path uploadPath = Path.of(videoDir).toAbsolutePath().normalize();
        Path targetPath = uploadPath.resolve(storedFilename).normalize();

        if (!targetPath.startsWith(uploadPath)) {
            throw new IllegalArgumentException("invalid file path");
        }

        try {
            Files.createDirectories(uploadPath);
            Files.copy(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to save video file", exception);
        }

        return VIDEO_URL_PREFIX + storedFilename;
    }

    @Override
    public String saveAudio(Path file, String filename) {
        if (file == null || !Files.exists(file)) {
            throw new IllegalArgumentException("audio file is required");
        }
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("audio filename is required");
        }

        Path audioRootPath = Path.of(audioDir).toAbsolutePath().normalize();
        Path targetPath = audioRootPath.resolve(filename).normalize();
        if (!targetPath.startsWith(audioRootPath)) {
            throw new IllegalArgumentException("invalid audio path");
        }

        try {
            Files.createDirectories(audioRootPath);
            Files.copy(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to save audio file", exception);
        }

        return AUDIO_URL_PREFIX + filename;
    }

    @Override
    public void validateVideoFilename(String filename) {
        validateExtension(getExtension(filename));
    }

    @Override
    public boolean saveChunk(String uploadId, Integer chunkIndex, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("chunk file is required");
        }

        Path uploadPath = getUploadChunkPath(uploadId);
        Path targetPath = uploadPath.resolve(chunkIndex + ".part").normalize();

        if (!targetPath.startsWith(uploadPath)) {
            throw new IllegalArgumentException("invalid chunk path");
        }

        try {
            Files.createDirectories(uploadPath);
            boolean alreadyUploaded = Files.exists(targetPath);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return alreadyUploaded;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to save chunk file", exception);
        }
    }

    @Override
    public String mergeChunks(String uploadId, String originalFilename, Integer totalChunks) {
        String extension = getExtension(originalFilename);
        validateExtension(extension);

        String storedFilename = UUID.randomUUID() + extension;
        Path uploadPath = getUploadChunkPath(uploadId);
        Path videoRootPath = Path.of(videoDir).toAbsolutePath().normalize();
        Path targetPath = videoRootPath.resolve(storedFilename).normalize();

        if (!targetPath.startsWith(videoRootPath)) {
            throw new IllegalArgumentException("invalid video path");
        }

        ensureAllChunksExist(uploadPath, totalChunks);

        try {
            Files.createDirectories(videoRootPath);
            try (OutputStream outputStream = Files.newOutputStream(targetPath)) {
                for (int i = 0; i < totalChunks; i++) {
                    Path chunkPath = uploadPath.resolve(i + ".part").normalize();
                    Files.copy(chunkPath, outputStream);
                }
            }
            return VIDEO_URL_PREFIX + storedFilename;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to merge chunk files", exception);
        }
    }

    @Override
    public void deleteChunks(String uploadId) {
        Path uploadPath = getUploadChunkPath(uploadId);
        if (!Files.exists(uploadPath)) {
            return;
        }

        try (var paths = Files.walk(uploadPath)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new IllegalStateException("failed to delete chunk file", exception);
                        }
                    });
        } catch (IOException exception) {
            throw new IllegalStateException("failed to delete chunk directory", exception);
        }
    }

    @Override
    public void deleteFile(String sourceUrl) {
        if (sourceUrl == null) {
            return;
        }

        String rootDir;
        String urlPrefix;
        if (sourceUrl.startsWith(VIDEO_URL_PREFIX)) {
            rootDir = videoDir;
            urlPrefix = VIDEO_URL_PREFIX;
        } else if (sourceUrl.startsWith(AUDIO_URL_PREFIX)) {
            rootDir = audioDir;
            urlPrefix = AUDIO_URL_PREFIX;
        } else {
            return; // 外部 URL 或未知前缀，静默忽略
        }

        String filename = sourceUrl.substring(urlPrefix.length());
        Path rootPath = Path.of(rootDir).toAbsolutePath().normalize();
        Path filePath = rootPath.resolve(filename).normalize();
        if (!filePath.startsWith(rootPath)) {
            throw new IllegalArgumentException("invalid file path");
        }

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to delete file " + filePath, exception);
        }
    }

    @Override
    public LocalAccess accessLocal(String sourceUrl) {
        return LocalAccess.wrap(resolveLocalPath(sourceUrl));
    }

    @Override
    public String publicUrl(String sourceUrl) {
        // Local storage: the static resource handler in CorsConfig serves /uploads/** directly,
        // so the internal URL doubles as the playback URL.
        return sourceUrl;
    }

    private Path resolveLocalPath(String sourceUrl) {
        if (sourceUrl == null) {
            throw new IllegalArgumentException("sourceUrl is required");
        }

        if (sourceUrl.startsWith(VIDEO_URL_PREFIX)) {
            return resolveLocalPath(sourceUrl, VIDEO_URL_PREFIX, videoDir, "video file does not exist");
        }

        if (sourceUrl.startsWith(AUDIO_URL_PREFIX)) {
            return resolveLocalPath(sourceUrl, AUDIO_URL_PREFIX, audioDir, "audio file does not exist");
        }

        throw new IllegalArgumentException("only local uploaded files are supported");
    }

    private Path resolveLocalPath(String sourceUrl, String urlPrefix, String rootDir, String missingMessage) {
        String filename = sourceUrl.substring(urlPrefix.length());
        Path rootPath = Path.of(rootDir).toAbsolutePath().normalize();
        Path filePath = rootPath.resolve(filename).normalize();
        if (!filePath.startsWith(rootPath)) {
            throw new IllegalArgumentException("invalid source path");
        }
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException(missingMessage);
        }
        return filePath;
    }

    private Path getUploadChunkPath(String uploadId) {
        Path chunkRootPath = Path.of(chunkDir).toAbsolutePath().normalize();
        Path uploadPath = chunkRootPath.resolve(uploadId).normalize();
        if (!uploadPath.startsWith(chunkRootPath)) {
            throw new IllegalArgumentException("invalid upload path");
        }
        return uploadPath;
    }

    private void ensureAllChunksExist(Path uploadPath, Integer totalChunks) {
        for (int i = 0; i < totalChunks; i++) {
            Path chunkPath = uploadPath.resolve(i + ".part").normalize();
            if (!chunkPath.startsWith(uploadPath) || !Files.exists(chunkPath)) {
                throw new IllegalArgumentException("chunk " + i + " is missing");
            }
        }
    }

    private void validateExtension(String extension) {
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("unsupported video file type");
        }
    }

    private String getExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        return filename.substring(dotIndex).toLowerCase(Locale.ROOT);
    }
}
