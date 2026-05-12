package com.videoinsight.backend.service;

public interface VideoImportTaskService {

    void submitImport(Long videoId, String sourceUrl);
}
