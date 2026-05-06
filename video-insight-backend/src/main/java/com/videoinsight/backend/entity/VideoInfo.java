package com.videoinsight.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video_info")
public class VideoInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String sourceUrl;

    private String status;

    private String summary;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
