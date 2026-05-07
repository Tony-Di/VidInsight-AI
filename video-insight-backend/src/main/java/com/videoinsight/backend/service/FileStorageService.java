package com.videoinsight.backend.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    String saveVideo(MultipartFile file);
}
