package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.hczk.hczkaiagentserver.enums.PlatformType;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("platform_configs")
@Data
public class PlatformConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private PlatformType platformType;

    @TableField("app_id")
    private String appId;

    @TableField("app_secret")
    private String appSecret;

    @TableField("webhook_url")
    private String webhookUrl;

    @TableField("auto_reply")
    private Boolean autoReply = false;

    private Boolean enabled = false;

    @TableField("agent_id")
    private Long agentId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
