package com.videoinsight.backend.service;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileStorageService {

    String saveVideo(MultipartFile file);

    String saveVideo(Path file, String originalFilename);

    /**
     * Persist a freshly produced audio file (e.g. ffmpeg output) to managed storage and
     * return its internal sourceUrl. The caller is responsible for deleting the input
     * temp path if it lives outside the audio root.
     */
    String saveAudio(Path file, String filename);

    void validateVideoFilename(String filename);

    boolean saveChunk(String uploadId, Integer chunkIndex, MultipartFile file);

    String mergeChunks(String uploadId, String originalFilename, Integer totalChunks);

    void deleteChunks(String uploadId);

    /**
     * Return a handle to a local-filesystem copy of the file referenced by sourceUrl.
     * Use with try-with-resources — for remote storage the underlying file is a temp copy
     * that close() will delete.
     */
    LocalAccess accessLocal(String sourceUrl);

    /**
     * Translate an internal sourceUrl (e.g. "/uploads/videos/xxx.mp4") into a URL the
     * browser can play. For local storage this is the same path (served by the static
     * resource handler). For S3/MinIO this is a presigned GET URL.
     */
    String publicUrl(String sourceUrl);

    /**
     * 删除存储中的视频/音频文件。仅处理以 /uploads/ 开头的内部路径，
     * 外部 URL（http/https）或不存在时静默忽略。
     */
    void deleteFile(String sourceUrl);
}
