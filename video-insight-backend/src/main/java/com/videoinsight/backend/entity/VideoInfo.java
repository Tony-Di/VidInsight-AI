package com.videoinsight.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.videoinsight.backend.enums.VideoStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video_info")
@Schema(description = "Video record")
public class VideoInfo {

    @TableId(type = IdType.AUTO)
    @Schema(description = "Video id.", example = "1")
    private Long id;

    @Schema(description = "Video title.", example = "Product demo video")
    private String title;

    @Schema(description = "External video URL or local uploaded file path.", example = "/uploads/videos/demo.mp4")
    private String sourceUrl;

    @TableField("status")
    @Schema(description = "Current video workflow status.", example = "PENDING")
    private VideoStatus videoStatus;

    @Schema(description = "Analysis summary generated for the video.", example = "This video explains the main product workflow.")
    private String summary;

    @Schema(description = "Creation time.")
    private LocalDateTime createdAt;

    @Schema(description = "Last update time.")
    private LocalDateTime updatedAt;

}
