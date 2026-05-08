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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.UUID;

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
            VideoInfo videoInfo = createVideoInfo(uploadTask, sourceUrl);

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
}
