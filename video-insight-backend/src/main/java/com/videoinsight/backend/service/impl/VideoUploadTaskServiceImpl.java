package com.videoinsight.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.entity.VideoUploadTask;
import com.videoinsight.backend.enums.UploadTaskStatus;
import com.videoinsight.backend.enums.VideoStatus;
import com.videoinsight.backend.exception.BusinessException;
import com.videoinsight.backend.mapper.VideoInfoMapper;
import com.videoinsight.backend.mapper.VideoUploadTaskMapper;
import com.videoinsight.backend.model.request.ChunkUploadInitRequest;
import com.videoinsight.backend.model.response.ChunkUploadInitResponse;
import com.videoinsight.backend.model.response.ChunkUploadResponse;
import com.videoinsight.backend.service.VideoUploadTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VideoUploadTaskServiceImpl extends ServiceImpl<VideoUploadTaskMapper, VideoUploadTask>
        implements VideoUploadTaskService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".mp4", ".mov", ".avi", ".mkv", ".webm");

    private final VideoInfoMapper videoInfoMapper;

    @Value("${app.upload.chunk-dir}")
    private String chunkDir;

    @Value("${app.upload.video-dir}")
    private String videoDir;

    @Override
    public ChunkUploadInitResponse initChunkUpload(ChunkUploadInitRequest request) {
        validateVideoExtension(request.getFileName());

        LocalDateTime now = LocalDateTime.now();
        String uploadId = UUID.randomUUID().toString();

        VideoUploadTask uploadTask = new VideoUploadTask();
        uploadTask.setUploadId(uploadId);
        uploadTask.setTitle(request.getTitle());
        uploadTask.setFileName(request.getFileName());
        uploadTask.setTotalChunks(request.getTotalChunks());
        uploadTask.setUploadedChunks(0);
        uploadTask.setStatus(UploadTaskStatus.UPLOADING);
        uploadTask.setCreatedAt(now);
        uploadTask.setUpdatedAt(now);

        save(uploadTask);
        return new ChunkUploadInitResponse(uploadId);
    }

    @Override
    public ChunkUploadResponse uploadChunk(String uploadId, Integer chunkIndex, MultipartFile file) {
        if (uploadId == null || uploadId.isBlank()) {
            throw new IllegalArgumentException("uploadId is required");
        }
        if (chunkIndex == null) {
            throw new IllegalArgumentException("chunkIndex is required");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("chunk file is required");
        }

        VideoUploadTask uploadTask = lambdaQuery()
                .eq(VideoUploadTask::getUploadId, uploadId)
                .one();
        if (uploadTask == null) {
            throw new BusinessException(404, "upload task does not exist");
        }
        if (uploadTask.getStatus() != UploadTaskStatus.UPLOADING) {
            throw new BusinessException(400, "upload task is not uploading");
        }
        if (chunkIndex < 0 || chunkIndex >= uploadTask.getTotalChunks()) {
            throw new IllegalArgumentException("chunkIndex is out of range");
        }

        boolean alreadyUploaded = saveChunkFile(uploadId, chunkIndex, file);
        if (!alreadyUploaded) {
            uploadTask.setUploadedChunks(uploadTask.getUploadedChunks() + 1);
        }
        uploadTask.setUpdatedAt(LocalDateTime.now());
        updateById(uploadTask);

        return new ChunkUploadResponse(
                uploadTask.getUploadId(),
                uploadTask.getUploadedChunks(),
                uploadTask.getTotalChunks()
        );
    }

    @Override
    public VideoInfo completeChunkUpload(String uploadId) {
        if (uploadId == null || uploadId.isBlank()) {
            throw new IllegalArgumentException("uploadId is required");
        }

        VideoUploadTask uploadTask = lambdaQuery()
                .eq(VideoUploadTask::getUploadId, uploadId)
                .one();
        if (uploadTask == null) {
            throw new BusinessException(404, "upload task does not exist");
        }
        if (uploadTask.getStatus() != UploadTaskStatus.UPLOADING) {
            throw new BusinessException(400, "upload task is not uploading");
        }

        Path uploadPath = getUploadChunkPath(uploadId);
        ensureAllChunksExist(uploadTask, uploadPath);

        String extension = getExtension(uploadTask.getFileName());
        String storedFilename = UUID.randomUUID() + extension;
        Path videoRootPath = Path.of(videoDir).toAbsolutePath().normalize();
        Path targetPath = videoRootPath.resolve(storedFilename).normalize();
        if (!targetPath.startsWith(videoRootPath)) {
            throw new IllegalArgumentException("invalid video path");
        }

        try {
            Files.createDirectories(videoRootPath);
            mergeChunks(uploadTask, uploadPath, targetPath);
            VideoInfo videoInfo = createVideoInfo(uploadTask, "/uploads/videos/" + storedFilename);

            uploadTask.setStatus(UploadTaskStatus.COMPLETED);
            uploadTask.setUpdatedAt(LocalDateTime.now());
            updateById(uploadTask);

            deleteDirectory(uploadPath);
            return videoInfo;
        } catch (IOException exception) {
            uploadTask.setStatus(UploadTaskStatus.FAILED);
            uploadTask.setUpdatedAt(LocalDateTime.now());
            updateById(uploadTask);
            throw new IllegalStateException("failed to complete chunk upload", exception);
        }
    }

    private boolean saveChunkFile(String uploadId, Integer chunkIndex, MultipartFile file) {
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

    private Path getUploadChunkPath(String uploadId) {
        Path chunkRootPath = Path.of(chunkDir).toAbsolutePath().normalize();
        Path uploadPath = chunkRootPath.resolve(uploadId).normalize();
        if (!uploadPath.startsWith(chunkRootPath)) {
            throw new IllegalArgumentException("invalid upload path");
        }
        return uploadPath;
    }

    private void ensureAllChunksExist(VideoUploadTask uploadTask, Path uploadPath) {
        for (int i = 0; i < uploadTask.getTotalChunks(); i++) {
            Path chunkPath = uploadPath.resolve(i + ".part").normalize();
            if (!chunkPath.startsWith(uploadPath) || !Files.exists(chunkPath)) {
                throw new BusinessException(400, "chunk " + i + " is missing");
            }
        }
    }

    private void mergeChunks(VideoUploadTask uploadTask, Path uploadPath, Path targetPath) throws IOException {
        try (OutputStream outputStream = Files.newOutputStream(targetPath)) {
            for (int i = 0; i < uploadTask.getTotalChunks(); i++) {
                Path chunkPath = uploadPath.resolve(i + ".part").normalize();
                Files.copy(chunkPath, outputStream);
            }
        }
    }

    private VideoInfo createVideoInfo(VideoUploadTask uploadTask, String sourceUrl) {
        LocalDateTime now = LocalDateTime.now();

        VideoInfo videoInfo = new VideoInfo();
        videoInfo.setTitle(uploadTask.getTitle());
        videoInfo.setSourceUrl(sourceUrl);
        videoInfo.setVideoStatus(VideoStatus.PENDING);
        videoInfo.setCreatedAt(now);
        videoInfo.setUpdatedAt(now);

        videoInfoMapper.insert(videoInfo);
        return videoInfo;
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new IllegalStateException("failed to delete chunk file", exception);
                        }
                    });
        }
    }

    private void validateVideoExtension(String filename) {
        String extension = getExtension(filename);
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
