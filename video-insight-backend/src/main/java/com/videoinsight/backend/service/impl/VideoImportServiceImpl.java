package com.videoinsight.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.enums.VideoStatus;
import com.videoinsight.backend.mapper.VideoInfoMapper;
import com.videoinsight.backend.model.request.VideoImportRequest;
import com.videoinsight.backend.service.VideoImportService;
import com.videoinsight.backend.service.VideoImportTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VideoImportServiceImpl extends ServiceImpl<VideoInfoMapper, VideoInfo> implements VideoImportService {

    private final VideoImportTaskService videoImportTaskService;

    @Override
    public VideoInfo importVideo(VideoImportRequest request) {
        LocalDateTime now = LocalDateTime.now();

        VideoInfo videoInfo = new VideoInfo();
        videoInfo.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle() : request.getSourceUrl());
        videoInfo.setSourceUrl(request.getSourceUrl());
        videoInfo.setVideoStatus(VideoStatus.IMPORTING);
        videoInfo.setCreatedAt(now);
        videoInfo.setUpdatedAt(now);

        save(videoInfo);
        videoImportTaskService.submitImport(videoInfo.getId(), request.getSourceUrl());
        return videoInfo;
    }
}
