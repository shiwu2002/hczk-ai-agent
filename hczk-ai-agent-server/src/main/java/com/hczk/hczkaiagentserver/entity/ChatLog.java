package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("chat_logs")
@Data
public class ChatLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("api_key_id")
    private Long apiKeyId;

    @TableField("model_id")
    private Long modelId;

    @TableField("model_name")
    private String modelName;

    @TableField("input_content")
    private String inputContent;

    @TableField("output_content")
    private String outputContent;

    @TableField("input_tokens")
    private Long inputTokens;

    @TableField("output_tokens")
    private Long outputTokens;

    private BigDecimal cost;

    @TableField("duration_ms")
    private Long durationMs;

    private String status;

    @TableField("error_message")
    private String errorMessage;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
