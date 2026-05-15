package com.videoinsight.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.videoinsight.backend.enums.UploadTaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video_upload_task")
@Schema(description = "Chunk upload task")
public class VideoUploadTask {

    @TableId(type = IdType.AUTO)
    @Schema(description = "Upload task database id.", example = "1")
    private Long id;

    @Schema(description = "Owner user id (app_user.id).", example = "42")
    private Long userId;

    @Schema(description = "Public upload task id used by clients.", example = "2f5b9c38-4e4b-47c1-89d7-5cdd9f81fca6")
    private String uploadId;

    @Schema(description = "Video title.", example = "Large lecture video")
    private String title;

    @Schema(description = "Original file name.", example = "lecture.mp4")
    private String fileName;

    @Schema(description = "Total number of chunks expected.", example = "12")
    private Integer totalChunks;

    @Schema(description = "Number of chunks already uploaded.", example = "3")
    private Integer uploadedChunks;

    @Schema(description = "Current chunk upload task status.", example = "UPLOADING")
    private UploadTaskStatus status;

    @Schema(description = "Creation time.")
    private LocalDateTime createdAt;

    @Schema(description = "Last update time.")
    private LocalDateTime updatedAt;
}
