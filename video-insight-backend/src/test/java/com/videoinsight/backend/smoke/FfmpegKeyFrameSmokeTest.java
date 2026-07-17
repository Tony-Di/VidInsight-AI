package com.videoinsight.backend.smoke;

import com.videoinsight.backend.service.MediaProcessingService;
import com.videoinsight.backend.service.impl.FfmpegMediaProcessingServiceImpl;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 依赖本机 ffmpeg(lavfi 合成测试片),不可用时跳过。 */
class FfmpegKeyFrameSmokeTest {

    private static final String FFMPEG =
            System.getenv().getOrDefault("FFMPEG_PATH", "ffmpeg");

    private static boolean ffmpegAvailable() {
        try {
            Process p = new ProcessBuilder(FFMPEG, "-version")
                    .redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void extractsSegmentsAndKeyFramesFromSynthesizedClip() throws Exception {
        Assumptions.assumeTrue(ffmpegAvailable(), "ffmpeg not on PATH, skipping");

        Path dir = Files.createTempDirectory("ffmpeg-smoke-");
        Path video = dir.resolve("test.mp4");
        Process synth = new ProcessBuilder(FFMPEG, "-y",
                "-f", "lavfi", "-i", "testsrc=duration=8:size=320x240:rate=10",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=8",
                "-pix_fmt", "yuv420p", "-shortest", video.toString())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        assertTrue(synth.waitFor(2, TimeUnit.MINUTES) && synth.exitValue() == 0, "无法合成测试视频");

        FfmpegMediaProcessingServiceImpl service = new FfmpegMediaProcessingServiceImpl(null, FFMPEG);

        List<Path> audio = service.extractAudioSegments(video, dir.resolve("audio"));
        assertEquals(1, audio.size(), "8 秒视频应切出恰好 1 个 60s 音频分片");

        List<MediaProcessingService.KeyFrame> frames = service.extractKeyFrames(video, dir.resolve("frames"));
        assertFalse(frames.isEmpty(), "select 滤镜的 eq(n,0) 保证至少第 0 帧被选中");
        assertTrue(frames.get(0).timestampMs() >= 0);
    }
}
