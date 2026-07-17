package com.videoinsight.backend.service;

import java.nio.file.Path;

public interface OcrService {

    /**
     * 对单张图片做 OCR,返回 trim 后的识别文本(可能为空串)。
     * 进程失败或超时抛 IllegalStateException,由调用方决定跳过该帧。
     */
    String recognize(Path image);
}
