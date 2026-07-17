package com.videoinsight.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.videoinsight.backend.enums.AgentTaskStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_analysis_task")
public class AgentAnalysisTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long videoId;

    private Long userId;

    /** 用户输入的分析目标,≤500 字符(controller 校验)。 */
    private String goal;

    /** goal 的 MD5,用于锁 key 与"同视频同目标"幂等查询。 */
    private String goalDigest;

    private AgentTaskStatus status;

    /** 实际执行的 Critic 轮数(1 或 2)。 */
    private Integer roundCount;

    private String planJson;

    private String answerJson;

    private String criticJson;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
