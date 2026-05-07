package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalFileStorageServiceImpl implements FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".mp4", ".mov", ".avi", ".mkv", ".webm");

    @Value("${app.upload.video-dir}")
    private String videoDir;

    @Override
    public String saveVideo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("video file is required");
        }

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "video" : file.getOriginalFilename()
        );
        String extension = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("unsupported video file type");
        }

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

        return "/uploads/videos/" + storedFilename;
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        return filename.substring(dotIndex).toLowerCase(Locale.ROOT);
    }
}
