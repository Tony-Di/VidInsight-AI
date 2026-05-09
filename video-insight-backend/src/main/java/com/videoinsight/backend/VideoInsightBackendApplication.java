package com.videoinsight.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.videoinsight.backend.mapper")
public class VideoInsightBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(VideoInsightBackendApplication.class, args);
    }

}
