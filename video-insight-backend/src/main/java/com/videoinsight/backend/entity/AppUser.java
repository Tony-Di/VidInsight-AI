package com.videoinsight.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("app_user")
@Schema(description = "Application user")
public class AppUser {

    @TableId(type = IdType.AUTO)
    @Schema(description = "User id.")
    private Long id;

    @Schema(description = "Login email, unique.")
    private String email;

    @Schema(description = "BCrypt password hash. Never returned to clients.")
    private String passwordHash;

    @Schema(description = "Display name. Falls back to email prefix when null.")
    private String displayName;

    @Schema(description = "Creation time.")
    private LocalDateTime createdAt;

    @Schema(description = "Last update time.")
    private LocalDateTime updatedAt;
}
