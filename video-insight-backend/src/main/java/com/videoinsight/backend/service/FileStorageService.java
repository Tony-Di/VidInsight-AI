package com.videoinsight.backend.service;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileStorageService {

    String saveVideo(MultipartFile file);

    String saveVideo(Path file, String originalFilename);

    void validateVideoFilename(String filename);

    boolean saveChunk(String uploadId, Integer chunkIndex, MultipartFile file);

    String mergeChunks(String uploadId, String originalFilename, Integer totalChunks);

    void deleteChunks(String uploadId);

    Path resolveLocalPath(String sourceUrl);

    /**
     * 删除本地上传的视频/音频文件。仅处理以 /uploads/ 开头的本地路径，
     * 外部 URL（http/https）或文件不存在时静默忽略。
     */
    void deleteFile(String sourceUrl);
}
