package com.videoinsight.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.siliconflow")
public class SiliconFlowProperties {

    private String apiKey;

    private String baseUrl;

    private String asrModel;

    private String chatModel;

    private String embeddingModel;
}
