package com.videoinsight.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.entity.VideoUploadTask;
import com.videoinsight.backend.model.request.ChunkUploadInitRequest;
import com.videoinsight.backend.model.response.ChunkUploadInitResponse;
import com.videoinsight.backend.model.response.ChunkUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface VideoUploadTaskService extends IService<VideoUploadTask> {

    ChunkUploadInitResponse initChunkUpload(ChunkUploadInitRequest request);

    ChunkUploadResponse uploadChunk(String uploadId, Integer chunkIndex, MultipartFile file);

    VideoInfo completeChunkUpload(String uploadId);
}
