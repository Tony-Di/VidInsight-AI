package com.videoinsight.backend.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.videoinsight.backend.common.PageResult;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.enums.VideoStatus;
import com.videoinsight.backend.exception.BusinessException;
import com.videoinsight.backend.mapper.VideoInfoMapper;
import com.videoinsight.backend.model.request.VideoCreateRequest;
import com.videoinsight.backend.service.FileStorageService;
import com.videoinsight.backend.service.VideoAnalysisTaskService;
import com.videoinsight.backend.service.VideoInfoService;
import com.videoinsight.backend.util.FileHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoInfoServiceImpl extends ServiceImpl<VideoInfoMapper, VideoInfo> implements VideoInfoService {

    private final FileStorageService fileStorageService;

    private final VideoAnalysisTaskService videoAnalysisTaskService;

    @Override
    public VideoInfo createVideo(VideoCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();

        VideoInfo videoInfo = new VideoInfo();
        videoInfo.setTitle(request.getTitle());
        videoInfo.setVideoStatus(VideoStatus.PENDING);
        videoInfo.setSourceUrl(request.getSourceUrl());
        videoInfo.setCreatedAt(now);
        videoInfo.setUpdatedAt(now);

        save(videoInfo);
        return videoInfo;
    }

    @Override
    public VideoInfo uploadVideo(MultipartFile file, String title) {
        String sourceUrl = fileStorageService.saveVideo(file);
        String videoTitle = StringUtils.hasText(title) ? title : file.getOriginalFilename();

        // MD5 去重
        String md5 = computeMd5OrNull(fileStorageService.resolveLocalPath(sourceUrl));
        if (md5 != null) {
            VideoInfo existing = getBaseMapper().findCompletedByMd5(md5);
            if (existing != null) {
                log.info("Duplicate video detected (md5={}), reusing result from videoId={}", md5, existing.getId());
                return existing;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        VideoInfo videoInfo = new VideoInfo();
        videoInfo.setTitle(videoTitle);
        videoInfo.setVideoStatus(VideoStatus.PENDING);
        videoInfo.setSourceUrl(sourceUrl);
        videoInfo.setFileMd5(md5);
        videoInfo.setCreatedAt(now);
        videoInfo.setUpdatedAt(now);

        save(videoInfo);
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
        Page<VideoInfo> result = lambdaQuery()
                .orderByDesc(VideoInfo::getCreatedAt)
                .page(new Page<>(page, pageSize));
        return new PageResult<>(result.getTotal(), page, pageSize, result.getRecords());
    }

    @Override
    public VideoInfo getVideoDetail(Long id) {
        return getById(id);
    }

    @Override
    public VideoInfo analyzeVideo(Long id) {
        VideoInfo videoInfo = getById(id);
        if (videoInfo == null) {
            throw new BusinessException(404, "video does not exist");
        }

        if (videoInfo.getVideoStatus() != VideoStatus.PENDING && videoInfo.getVideoStatus() != VideoStatus.FAILED) {
            throw new BusinessException(400, "video status does not allow analysis");
        }

        videoInfo.setVideoStatus(VideoStatus.PROCESSING);
        videoInfo.setUpdatedAt(LocalDateTime.now());
        updateById(videoInfo);

        videoAnalysisTaskService.submitAnalysis(videoInfo.getId());

        return videoInfo;
    }
}
