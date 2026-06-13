package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.hczk.hczkaiagentserver.enums.AgentStatus;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("agents")
@Data
public class Agent {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    @TableField("model_id")
    private Long modelId;

    @TableField("user_id")
    private Long userId;

    private AgentStatus status = AgentStatus.ACTIVE;

    @TableField("agent_type")
    private String agentType;

    @TableField("total_calls")
    private Long totalCalls = 0L;

    @TableField("total_tokens")
    private Long totalTokens = 0L;

    @TableField("avg_latency")
    private Integer avgLatency;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
