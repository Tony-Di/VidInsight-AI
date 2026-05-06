package com.videoinsight.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.mapper.VideoInfoMapper;
import com.videoinsight.backend.model.request.VideoCreateRequest;
import com.videoinsight.backend.service.VideoInfoService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class VideoInfoServiceImpl extends ServiceImpl<VideoInfoMapper, VideoInfo> implements VideoInfoService {

    private static final String STATUS_PENDING = "PENDING";

    @Override
    public VideoInfo createVideo(VideoCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();

        VideoInfo videoInfo = new VideoInfo();
        videoInfo.setTitle(request.getTitle());
        videoInfo.setSourceUrl(request.getSourceUrl());
        videoInfo.setStatus(STATUS_PENDING);
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
}
