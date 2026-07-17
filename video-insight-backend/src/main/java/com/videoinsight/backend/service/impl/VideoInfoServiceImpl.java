package com.videoinsight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.videoinsight.backend.common.PageResult;
import com.videoinsight.backend.entity.AgentAnalysisTask;
import com.videoinsight.backend.entity.VideoAgentContext;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.enums.VideoStatus;
import com.videoinsight.backend.exception.BusinessException;
import com.videoinsight.backend.mapper.AgentAnalysisTaskMapper;
import com.videoinsight.backend.mapper.VideoAgentContextMapper;
import com.videoinsight.backend.mapper.VideoInfoMapper;
import com.videoinsight.backend.model.request.VideoCreateRequest;
import com.videoinsight.backend.security.SecurityUtil;
import com.videoinsight.backend.service.FileStorageService;
import com.videoinsight.backend.service.LocalAccess;
import com.videoinsight.backend.service.VideoAnalysisTaskService;
import com.videoinsight.backend.service.VideoCacheService;
import com.videoinsight.backend.service.VideoInfoService;
import com.videoinsight.backend.util.FileHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoInfoServiceImpl extends ServiceImpl<VideoInfoMapper, VideoInfo> implements VideoInfoService {

    private final FileStorageService fileStorageService;

    private final VideoAnalysisTaskService videoAnalysisTaskService;

    private final VideoCacheService videoCacheService;

    private final VideoAgentContextMapper videoAgentContextMapper;

    private final AgentAnalysisTaskMapper agentAnalysisTaskMapper;

    @Override
    public VideoInfo createVideo(VideoCreateRequest request) {
        Long userId = SecurityUtil.currentUserId();
        LocalDateTime now = LocalDateTime.now();

        VideoInfo videoInfo = new VideoInfo();
        videoInfo.setUserId(userId);
        videoInfo.setTitle(request.getTitle());
        videoInfo.setVideoStatus(VideoStatus.PENDING);
        videoInfo.setSourceUrl(request.getSourceUrl());
        videoInfo.setCreatedAt(now);
        videoInfo.setUpdatedAt(now);

        save(videoInfo);
        videoCacheService.evictUserLists(userId);
        return videoInfo;
    }

    @Override
    public VideoInfo uploadVideo(MultipartFile file, String title) {
        Long userId = SecurityUtil.currentUserId();
        String sourceUrl = fileStorageService.saveVideo(file);
        String videoTitle = StringUtils.hasText(title) ? title : file.getOriginalFilename();

        // MD5 去重(仅当前用户范围内,避免跨用户泄漏私有 transcript/summary)
        String md5;
        try (LocalAccess access = fileStorageService.accessLocal(sourceUrl)) {
            md5 = computeMd5OrNull(access.path());
        }
        if (md5 != null) {
            VideoInfo existing = getBaseMapper().findCompletedByMd5AndUser(md5, userId);
            if (existing != null) {
                log.info("Duplicate video detected (md5={}, userId={}), reusing result from videoId={}",
                        md5, userId, existing.getId());
                return existing;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        VideoInfo videoInfo = new VideoInfo();
        videoInfo.setUserId(userId);
        videoInfo.setTitle(videoTitle);
        videoInfo.setVideoStatus(VideoStatus.PENDING);
        videoInfo.setSourceUrl(sourceUrl);
        videoInfo.setFileMd5(md5);
        videoInfo.setCreatedAt(now);
        videoInfo.setUpdatedAt(now);

        save(videoInfo);
        videoCacheService.evictUserLists(userId);
        return videoInfo;
    }

    private String computeMd5OrNull(java.nio.file.Path path) {
        try {
            return FileHashUtil.md5(path);
        } catch (IOException e) {
            log.warn("Failed to compute MD5 for {}: {}", path, e.getMessage());
            return null;
        }
    }

    @Override
    public PageResult<VideoInfo> listVideos(int page, int pageSize) {
        Long userId = SecurityUtil.currentUserId();
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));

        PageResult<VideoInfo> cached = videoCacheService.getList(userId, safePage, safePageSize);
        if (cached != null) {
            return cached;
        }

        long total = lambdaQuery().eq(VideoInfo::getUserId, userId).count();
        List<VideoInfo> records = lambdaQuery()
                .eq(VideoInfo::getUserId, userId)
                .orderByDesc(VideoInfo::getCreatedAt)
                .last("LIMIT " + safePageSize + " OFFSET " + (long) (safePage - 1) * safePageSize)
                .list();
        PageResult<VideoInfo> result = new PageResult<>(total, safePage, safePageSize, records);
        videoCacheService.setList(userId, safePage, safePageSize, result);
        return result;
    }

    @Override
    public VideoInfo getVideoDetail(Long id) {
        Long userId = SecurityUtil.currentUserId();
        // Cache Aside + 防穿透(空值哨兵)+ 防击穿(回源互斥锁)+ 防雪崩(TTL 抖动)
        // 缓存不按用户分(同一个视频内容对所有人一样),所有权检查在外层做。
        VideoInfo videoInfo = videoCacheService.getDetailOrLoad(id, () -> getById(id));
        if (videoInfo == null) {
            throw new BusinessException(404, "video does not exist");
        }
        if (!userId.equals(videoInfo.getUserId())) {
            // 注意不要返回 404 vs 403 不同的状态——会泄漏"该 id 存在但不属于你"。
            // 但这里项目还是返回 403 方便调试,生产可考虑统一返 404。
            throw new BusinessException(403, "you do not own this video");
        }
        return videoInfo;
    }

    @Override
    public void deleteVideo(Long id) {
        Long userId = SecurityUtil.currentUserId();
        VideoInfo videoInfo = getById(id);
        if (videoInfo == null) {
            throw new BusinessException(404, "video does not exist");
        }
        if (!userId.equals(videoInfo.getUserId())) {
            throw new BusinessException(403, "you do not own this video");
        }

        String sourceUrl = videoInfo.getSourceUrl();
        String audioUrl = videoInfo.getAudioUrl();

        removeById(id);
        // 级联清理 agent 数据:上下文缓存与问答任务都以 videoId 挂靠,视频没了它们就是孤儿行
        videoAgentContextMapper.delete(new LambdaQueryWrapper<VideoAgentContext>()
                .eq(VideoAgentContext::getVideoId, id));
        agentAnalysisTaskMapper.delete(new LambdaQueryWrapper<AgentAnalysisTask>()
                .eq(AgentAnalysisTask::getVideoId, id));
        videoCacheService.evictDetail(id);
        videoCacheService.evictUserLists(userId);

        // 仅当没有其他记录引用同一物理文件时才删除（MD5 去重可能导致多条记录共用文件）
        if (StringUtils.hasText(sourceUrl)) {
            long sourceRefs = lambdaQuery().eq(VideoInfo::getSourceUrl, sourceUrl).count();
            if (sourceRefs == 0) {
                try {
                    fileStorageService.deleteFile(sourceUrl);
                } catch (Exception e) {
                    log.warn("Failed to delete video file {}: {}", sourceUrl, e.getMessage());
                }
            }
        }
        if (StringUtils.hasText(audioUrl)) {
            long audioRefs = lambdaQuery().eq(VideoInfo::getAudioUrl, audioUrl).count();
            if (audioRefs == 0) {
                try {
                    fileStorageService.deleteFile(audioUrl);
                } catch (Exception e) {
                    log.warn("Failed to delete audio file {}: {}", audioUrl, e.getMessage());
                }
            }
        }
    }

    @Override
    public VideoInfo cancelAnalysis(Long id) {
        Long userId = SecurityUtil.currentUserId();
        VideoInfo videoInfo = getById(id);
        if (videoInfo == null) {
            throw new BusinessException(404, "video does not exist");
        }
        if (!userId.equals(videoInfo.getUserId())) {
            throw new BusinessException(403, "you do not own this video");
        }
        if (videoInfo.getVideoStatus() != VideoStatus.PROCESSING) {
            throw new BusinessException(400, "video is not being analyzed");
        }

        // 重新分析的取消:已有转写说明之前完成过,回退 COMPLETED 保留原结果。
        // 排队中/进行中的重分析消息会被消费端的 COMPLETED 幂等检查自然跳过。
        if (StringUtils.hasText(videoInfo.getTranscript())) {
            videoInfo.setVideoStatus(VideoStatus.COMPLETED);
            videoInfo.setUpdatedAt(LocalDateTime.now());
            updateById(videoInfo);
            videoCacheService.evictDetail(id);
            videoCacheService.evictUserLists(userId);
            return videoInfo;
        }

        // 首次分析的取消:半途而废的记录没有保留价值,沿用原有的整条移除语义。
        deleteVideo(id);
        return null;
    }

    @Override
    public VideoInfo analyzeVideo(Long id) {
        Long userId = SecurityUtil.currentUserId();
        VideoInfo videoInfo = getById(id);
        if (videoInfo == null) {
            throw new BusinessException(404, "video does not exist");
        }
        if (!userId.equals(videoInfo.getUserId())) {
            throw new BusinessException(403, "you do not own this video");
        }

        if (videoInfo.getVideoStatus() != VideoStatus.PENDING
                && videoInfo.getVideoStatus() != VideoStatus.FAILED
                && videoInfo.getVideoStatus() != VideoStatus.COMPLETED) {
            throw new BusinessException(400, "video status does not allow analysis");
        }

        videoInfo.setVideoStatus(VideoStatus.PROCESSING);
        videoInfo.setUpdatedAt(LocalDateTime.now());
        updateById(videoInfo);
        videoCacheService.evictDetail(videoInfo.getId());
        videoCacheService.evictUserLists(userId);

        videoAnalysisTaskService.submitAnalysis(videoInfo.getId());

        return videoInfo;
    }
}
