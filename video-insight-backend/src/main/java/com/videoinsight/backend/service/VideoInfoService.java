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

    void deleteVideo(Long id);

    /**
     * 取消正在进行的分析。已有历史结果(transcript 非空,即重新分析场景)时回退为
     * COMPLETED 并保留记录,返回该视频;首次分析没有可保留的结果,整条移除并返回 null。
     */
    VideoInfo cancelAnalysis(Long id);
}
