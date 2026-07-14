package com.videoinsight.backend.it;

import com.videoinsight.backend.security.AppUserPrincipal;
import com.videoinsight.backend.service.VideoInfoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 删除视频必须级联清理 agent 上下文与问答任务,否则留下无主的孤儿行。 */
class VideoDeleteCleanupIT extends IntegrationTestBase {

    private static final long USER_ID = 42L;

    @Autowired
    private VideoInfoService videoInfoService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deleteVideo_removesAgentContextAndTasks() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AppUserPrincipal(USER_ID, "it@test.local"), null, List.of()));

        jdbcTemplate.update(
                "INSERT INTO video_info (user_id, title, status, created_at, updated_at) "
                        + "VALUES (?, 'cleanup-it', 'COMPLETED', NOW(), NOW())", USER_ID);
        Long videoId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM video_info", Long.class);
        jdbcTemplate.update(
                "INSERT INTO video_agent_context (video_id, context_json) VALUES (?, '{}')", videoId);
        jdbcTemplate.update(
                "INSERT INTO agent_analysis_task (video_id, user_id, goal, goal_digest, status) "
                        + "VALUES (?, ?, 'g', 'd', 'COMPLETED')", videoId, USER_ID);

        videoInfoService.deleteVideo(videoId);

        Integer ctxRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_agent_context WHERE video_id = ?", Integer.class, videoId);
        Integer taskRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_analysis_task WHERE video_id = ?", Integer.class, videoId);
        assertThat(ctxRows).isZero();
        assertThat(taskRows).isZero();
    }
}
