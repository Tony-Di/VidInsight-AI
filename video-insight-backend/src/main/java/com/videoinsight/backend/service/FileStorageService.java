package com.videoinsight.backend.service;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileStorageService {

    String saveVideo(MultipartFile file);

    void validateVideoFilename(String filename);

    boolean saveChunk(String uploadId, Integer chunkIndex, MultipartFile file);

    String mergeChunks(String uploadId, String originalFilename, Integer totalChunks);

    void deleteChunks(String uploadId);

    Path resolveLocalPath(String sourceUrl);
}
