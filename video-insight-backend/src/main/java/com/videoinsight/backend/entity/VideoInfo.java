package com.videoinsight.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.videoinsight.backend.enums.VideoStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video_info")
public class VideoInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String sourceUrl;

    @TableField("status")
    private VideoStatus videoStatus;

    private String summary;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
