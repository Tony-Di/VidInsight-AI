package com.videoinsight.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.enums.VideoStatus;
import com.videoinsight.backend.exception.BusinessException;
import com.videoinsight.backend.mapper.VideoInfoMapper;
import com.videoinsight.backend.model.request.VideoCreateRequest;
import com.videoinsight.backend.service.FileStorageService;
import com.videoinsight.backend.service.VideoAnalysisTaskService;
import com.videoinsight.backend.service.VideoInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

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
        LocalDateTime now = LocalDateTime.now();

        VideoInfo videoInfo = new VideoInfo();
        videoInfo.setTitle(videoTitle);
        videoInfo.setVideoStatus(VideoStatus.PENDING);
        videoInfo.setSourceUrl(sourceUrl);
        videoInfo.setCreatedAt(now);
        videoInfo.setUpdatedAt(now);

        save(videoInfo);
        return videoInfo;
    }

    @Override
    public List<VideoInfo> listVideos() {
        return lambdaQuery()
                .orderByDesc(VideoInfo::getCreatedAt)
                .list();
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
