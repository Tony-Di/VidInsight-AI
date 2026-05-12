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
import com.videoinsight.backend.service.FileStorageService;
import com.videoinsight.backend.service.VideoUploadTaskService;
import com.videoinsight.backend.util.FileHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoUploadTaskServiceImpl extends ServiceImpl<VideoUploadTaskMapper, VideoUploadTask>
        implements VideoUploadTaskService {

    private final VideoInfoMapper videoInfoMapper;

    private final FileStorageService fileStorageService;

    @Override
    public ChunkUploadInitResponse initChunkUpload(ChunkUploadInitRequest request) {
        fileStorageService.validateVideoFilename(request.getFileName());

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

        boolean alreadyUploaded = fileStorageService.saveChunk(uploadId, chunkIndex, file);
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

        try {
            String sourceUrl = fileStorageService.mergeChunks(
                    uploadTask.getUploadId(),
                    uploadTask.getFileName(),
                    uploadTask.getTotalChunks()
            );

            // MD5 去重：合并完成后计算文件哈希，复用已有的分析结果
            String md5 = computeMd5OrNull(fileStorageService.resolveLocalPath(sourceUrl));
            if (md5 != null) {
                VideoInfo existing = videoInfoMapper.findCompletedByMd5(md5);
                if (existing != null) {
                    log.info("Duplicate video detected (md5={}), reusing result from videoId={}", md5, existing.getId());
                    uploadTask.setStatus(UploadTaskStatus.COMPLETED);
                    uploadTask.setUpdatedAt(LocalDateTime.now());
                    updateById(uploadTask);
                    fileStorageService.deleteChunks(uploadTask.getUploadId());
                    return existing;
                }
            }

            VideoInfo videoInfo = createVideoInfo(uploadTask, sourceUrl, md5);

            uploadTask.setStatus(UploadTaskStatus.COMPLETED);
            uploadTask.setUpdatedAt(LocalDateTime.now());
            updateById(uploadTask);

            fileStorageService.deleteChunks(uploadTask.getUploadId());
            return videoInfo;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            uploadTask.setStatus(UploadTaskStatus.FAILED);
            uploadTask.setUpdatedAt(LocalDateTime.now());
            updateById(uploadTask);
            throw new IllegalStateException("failed to complete chunk upload", exception);
        }
    }

    private VideoInfo createVideoInfo(VideoUploadTask uploadTask, String sourceUrl, String md5) {
        LocalDateTime now = LocalDateTime.now();

        VideoInfo videoInfo = new VideoInfo();
        videoInfo.setTitle(uploadTask.getTitle());
        videoInfo.setSourceUrl(sourceUrl);
        videoInfo.setFileMd5(md5);
        videoInfo.setVideoStatus(VideoStatus.PENDING);
        videoInfo.setCreatedAt(now);
        videoInfo.setUpdatedAt(now);

        videoInfoMapper.insert(videoInfo);
        return videoInfo;
    }

    private String computeMd5OrNull(java.nio.file.Path path) {
        try {
            return FileHashUtil.md5(path);
        } catch (IOException e) {
            log.warn("Failed to compute MD5 for {}: {}", path, e.getMessage());
            return null;
        }
    }
}
