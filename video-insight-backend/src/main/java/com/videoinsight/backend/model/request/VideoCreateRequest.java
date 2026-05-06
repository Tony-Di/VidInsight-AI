package com.videoinsight.backend.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VideoCreateRequest {

    @NotBlank(message = "title is required")
    private String title;

    @NotBlank(message = "sourceUrl is required")
    private String sourceUrl;
}
