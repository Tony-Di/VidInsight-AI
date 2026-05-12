package com.videoinsight.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.ytdlp")
public class YtDlpProperties {

    private String path;

    private String ffmpegLocation;

    private int downloadTimeoutMinutes = 30;
}
