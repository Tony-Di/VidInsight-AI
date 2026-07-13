package com.videoinsight.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video_agent_context")
public class VideoAgentContext {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long videoId;

    /** VideoContextData 的 JSON(与目标无关,每视频构建一次)。 */
    private String contextJson;

    /** List&lt;VideoChunk&gt; 的 JSON(5 分钟粒度检索索引),可空,首次检索时回填。 */
    private String chunksJson;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
