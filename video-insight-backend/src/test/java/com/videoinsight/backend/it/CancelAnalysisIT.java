package com.videoinsight.backend.it;

import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.enums.VideoStatus;
import com.videoinsight.backend.exception.BusinessException;
import com.videoinsight.backend.security.AppUserPrincipal;
import com.videoinsight.backend.service.VideoInfoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 取消分析的两种语义:重新分析的取消要保留原结果(回退 COMPLETED),
 * 首次分析的取消才移除记录(原有行为)。
 */
class CancelAnalysisIT extends IntegrationTestBase {

    private static final long USER_ID = 42L;

    @Autowired
    private VideoInfoService videoInfoService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AppUserPrincipal(userId, "it@test.local"), null, List.of()));
    }

    private long insertVideo(String transcript) {
        jdbcTemplate.update(
                "INSERT INTO video_info (user_id, title, status, transcript, created_at, updated_at) "
                        + "VALUES (?, ?, 'PROCESSING', ?, NOW(), NOW())",
                USER_ID, "cancel-it", transcript);
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM video_info", Long.class);
        return id;
    }

    @Test
    void cancelOnReanalysis_keepsRecordAndRevertsToCompleted() {
        loginAs(USER_ID);
        long id = insertVideo("之前分析留下的转写文本");

        VideoInfo kept = videoInfoService.cancelAnalysis(id);

        assertThat(kept).isNotNull();
        assertThat(kept.getVideoStatus()).isEqualTo(VideoStatus.COMPLETED);
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_info WHERE id = ?", Integer.class, id);
        assertThat(rows).isEqualTo(1);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM video_info WHERE id = ?", String.class, id);
        assertThat(status).isEqualTo("COMPLETED");
    }

    @Test
    void cancelOnFirstAnalysis_removesRecord() {
        loginAs(USER_ID);
        long id = insertVideo(null);

        VideoInfo removed = videoInfoService.cancelAnalysis(id);

        assertThat(removed).isNull();
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_info WHERE id = ?", Integer.class, id);
        assertThat(rows).isZero();
    }

    @Test
    void cancelOnNonProcessingVideo_isRejected() {
        loginAs(USER_ID);
        jdbcTemplate.update(
                "INSERT INTO video_info (user_id, title, status, transcript, created_at, updated_at) "
                        + "VALUES (?, ?, 'COMPLETED', ?, NOW(), NOW())",
                USER_ID, "cancel-it", "text");
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM video_info", Long.class);

        assertThatThrownBy(() -> videoInfoService.cancelAnalysis(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not being analyzed");
    }

    @Test
    void cancelSomeoneElsesVideo_isForbidden() {
        loginAs(USER_ID);
        long id = insertVideo("text");
        loginAs(99L);

        assertThatThrownBy(() -> videoInfoService.cancelAnalysis(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("own");
    }
}
