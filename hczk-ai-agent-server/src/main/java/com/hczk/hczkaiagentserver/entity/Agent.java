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

    /**
     * 智能体类型：
     * - MODEL: 直接绑定模型调用
     * - SKILL: 绑定 Skill 配置包，走通用运行时
     * - ENDPOINT: 定制化智能体，转发到外部地址
     */
    @TableField("agent_type")
    private String agentType = "MODEL";

    /**
     * Model 模式：绑定的模型 ID
     */
    @TableField("model_id")
    private Long modelId;

    /**
     * Skill 模式：绑定的 Skill ID
     */
    @TableField("skill_id")
    private String skillId;

    /**
     * Endpoint 模式：定制智能体地址
     */
    @TableField("endpoint")
    private String endpoint;

    /**
     * Endpoint 模式：认证头
     */
    @TableField("endpoint_auth_header")
    private String endpointAuthHeader;

    /**
     * 归属用户 ID（管理员创建）
     */
    @TableField("user_id")
    private Long userId;

    private AgentStatus status = AgentStatus.ACTIVE;

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
