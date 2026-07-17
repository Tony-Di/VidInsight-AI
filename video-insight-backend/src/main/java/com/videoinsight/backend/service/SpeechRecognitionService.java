package com.videoinsight.backend.service;

import java.nio.file.Path;

public interface SpeechRecognitionService {

    String transcribe(String audioUrl);

    /** 直接转写一个本地音频文件(60s 分片场景),不经过 FileStorageService。 */
    String transcribeFile(Path audioFile);
}
