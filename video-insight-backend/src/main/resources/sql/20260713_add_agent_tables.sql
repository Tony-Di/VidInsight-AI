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
