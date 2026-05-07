package com.videoinsight.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI videoInsightOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("VidInsight AI Backend API")
                        .version("0.0.1")
                        .description("API documentation for video upload, chunk upload, and analysis workflows."));
    }
}
