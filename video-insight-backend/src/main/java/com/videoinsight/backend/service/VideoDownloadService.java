package com.videoinsight.backend.service;

import java.nio.file.Path;

public interface VideoDownloadService {

    Path download(String sourceUrl);
}
