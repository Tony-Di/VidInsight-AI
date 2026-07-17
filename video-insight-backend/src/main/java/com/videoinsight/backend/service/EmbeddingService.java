package com.videoinsight.backend.service;

import java.util.List;

public interface EmbeddingService {

    /** 文本向量化(BAAI/bge-m3)。失败抛 IllegalStateException,由检索层降级为关键词打分。 */
    List<Double> embed(String text);
}
