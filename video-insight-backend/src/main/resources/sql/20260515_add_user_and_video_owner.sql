-- 用户表。表名用 app_user 避免和 MySQL 8 保留字 user 冲突。
CREATE TABLE app_user (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    email           VARCHAR(190) NOT NULL COMMENT '登录邮箱,作为唯一身份标识',
    password_hash   VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    display_name    VARCHAR(80)  NULL     COMMENT '显示名,可空,空则用 email 前缀',
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 给视频表加归属用户列。
-- 注意:开发期为了避免遗留视频归属空导致列表查询逻辑特判,直接清空旧数据。
-- 上线后绝对不能这么干,届时应该建一个 'system' 占位用户接管旧数据。
DELETE FROM video_info;
DELETE FROM video_upload_task;

ALTER TABLE video_info
    ADD COLUMN user_id BIGINT NOT NULL COMMENT '视频所有者(app_user.id)' AFTER id;

CREATE INDEX idx_video_info_user_id ON video_info(user_id);
CREATE INDEX idx_video_info_user_created ON video_info(user_id, created_at);

ALTER TABLE video_upload_task
    ADD COLUMN user_id BIGINT NOT NULL COMMENT '上传任务所有者,防止用户 A 续传用户 B 的任务' AFTER id;

CREATE INDEX idx_video_upload_task_user_id ON video_upload_task(user_id);
