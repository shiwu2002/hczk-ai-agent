package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("merchant_agent_binding")
@Data
public class MerchantAgentBinding {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 绑定的用户 ID（雪花ID，关联 users.user_id）
     */
    @TableField("user_id")
    private String userId;

    /**
     * 绑定的平台注册智能体 ID（优先级最高）
     */
    @TableField("agent_id")
    private Long agentId;

    @TableField("agent_endpoint")
    private String agentEndpoint;

    @TableField("agent_auth_header")
    private String agentAuthHeader;

    @TableField("persona_override")
    private String personaOverride;

    @TableField("capabilities_override")
    private String capabilitiesOverride;

    @TableField("config_override")
    private String configOverride;

    @TableField("tools_config")
    private String toolsConfig;

    private Boolean enabled = true;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}