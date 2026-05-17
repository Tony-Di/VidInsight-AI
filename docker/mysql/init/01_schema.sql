-- VidInsight-AI 完整初始化 Schema
-- 包含所有迁移脚本的最终状态，新环境直接初始化无需再跑增量脚本

CREATE TABLE IF NOT EXISTS app_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(190) NOT NULL COMMENT '登录邮箱，作为唯一身份标识',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    display_name  VARCHAR(80)  NULL     COMMENT '显示名，可空，空则用 email 前缀',
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS video_info (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL COMMENT '视频所有者(app_user.id)',
    title       VARCHAR(255) NULL,
    source_url  VARCHAR(500) NULL,
    file_md5    VARCHAR(32)  NULL     COMMENT '视频文件 MD5，用于内容去重',
    audio_url   VARCHAR(500) NULL,
    transcript  LONGTEXT     NULL,
    status      VARCHAR(50)  NOT NULL,
    summary     LONGTEXT     NULL,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_video_info_file_md5 (file_md5),
    INDEX idx_video_info_user_id (user_id),
    INDEX idx_video_info_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS video_upload_task (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL COMMENT '上传任务所有者',
    upload_id       VARCHAR(36) NOT NULL COMMENT 'UUID',
    title           VARCHAR(255) NULL,
    file_name       VARCHAR(255) NULL,
    total_chunks    INT         NOT NULL,
    uploaded_chunks INT         NOT NULL DEFAULT 0,
    status          VARCHAR(50) NOT NULL,
    created_at      DATETIME    NOT NULL,
    updated_at      DATETIME    NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_video_upload_task_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
