package com.videoinsight.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.videoinsight.backend.common.PageResult;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.model.request.VideoCreateRequest;
import org.springframework.web.multipart.MultipartFile;

public interface VideoInfoService extends IService<VideoInfo> {

    VideoInfo createVideo(VideoCreateRequest request);

    VideoInfo uploadVideo(MultipartFile file, String title);

    PageResult<VideoInfo> listVideos(int page, int pageSize);

    VideoInfo getVideoDetail(Long id);

    VideoInfo analyzeVideo(Long id);
}
