package com.videoinsight.backend.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentAskRequest {

    @NotBlank
    @Size(max = 500)
    @Schema(description = "分析目标,如:整理本视频的全部知识点并给出时间戳", example = "总结这节课的重点")
    private String goal;
}
