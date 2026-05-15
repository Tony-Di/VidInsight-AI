package com.videoinsight.backend.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.enums.VideoStatus;

public interface VideoInfoMapper extends BaseMapper<VideoInfo> {

    /**
     * 按文件 MD5 查找当前用户的已完成视频记录,用于内容去重复用结果。
     * 跨用户不复用——避免用户 A 上传相同视频时直接拿到用户 B 的私有 transcript/summary。
     */
    default VideoInfo findCompletedByMd5AndUser(String md5, Long userId) {
        return selectOne(
                new LambdaQueryWrapper<VideoInfo>()
                        .eq(VideoInfo::getFileMd5, md5)
                        .eq(VideoInfo::getUserId, userId)
                        .eq(VideoInfo::getVideoStatus, VideoStatus.COMPLETED)
                        .last("LIMIT 1")
        );
    }
}
