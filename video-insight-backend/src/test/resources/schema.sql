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
-- Video Agent: 多模态上下文缓存(每视频一行,懒构建)
CREATE TABLE IF NOT EXISTS video_agent_context (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id BIGINT NOT NULL,
    context_json LONGTEXT NOT NULL,
    chunks_json LONGTEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_ctx_video (video_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Video Agent: 问答任务(视频 + 目标 一次分析一行)
CREATE TABLE IF NOT EXISTS agent_analysis_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    goal VARCHAR(500) NOT NULL,
    goal_digest CHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL,
    round_count INT NOT NULL DEFAULT 0,
    plan_json TEXT NULL,
    answer_json MEDIUMTEXT NULL,
    critic_json TEXT NULL,
    error_message VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_agent_task_video (video_id),
    KEY idx_agent_task_video_goal (video_id, goal_digest)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
