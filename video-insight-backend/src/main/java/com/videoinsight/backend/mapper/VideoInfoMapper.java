package com.videoinsight.backend.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.enums.VideoStatus;

public interface VideoInfoMapper extends BaseMapper<VideoInfo> {

    /**
     * 按文件 MD5 查找已完成的视频记录，用于内容去重复用结果。
     */
    default VideoInfo findCompletedByMd5(String md5) {
        return selectOne(
                new LambdaQueryWrapper<VideoInfo>()
                        .eq(VideoInfo::getFileMd5, md5)
                        .eq(VideoInfo::getVideoStatus, VideoStatus.COMPLETED)
                        .last("LIMIT 1")
        );
    }
}
