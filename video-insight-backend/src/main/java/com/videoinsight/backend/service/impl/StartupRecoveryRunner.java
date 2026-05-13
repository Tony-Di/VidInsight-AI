package com.videoinsight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.videoinsight.backend.entity.VideoInfo;
import com.videoinsight.backend.enums.VideoStatus;
import com.videoinsight.backend.mapper.VideoInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupRecoveryRunner implements ApplicationRunner {

    private final VideoInfoMapper videoInfoMapper;

    @Override
    public void run(ApplicationArguments args) {
        int updated = videoInfoMapper.update(null,
                new LambdaUpdateWrapper<VideoInfo>()
                        .eq(VideoInfo::getVideoStatus, VideoStatus.IMPORTING)
                        .set(VideoInfo::getVideoStatus, VideoStatus.IMPORT_FAILED)
                        .set(VideoInfo::getSummary, "Import interrupted by service restart")
                        .set(VideoInfo::getUpdatedAt, LocalDateTime.now())
        );
        if (updated > 0) {
            log.warn("Recovered {} stuck IMPORTING record(s) to IMPORT_FAILED on startup.", updated);
        }
    }
}
