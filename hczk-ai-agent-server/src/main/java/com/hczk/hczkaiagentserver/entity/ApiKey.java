package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("api_keys")
@Data
public class ApiKey {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField("api_key")
    private String apiKey;

    @TableField("user_id")
    private Long userId;

    @TableField("total_calls")
    private Long totalCalls = 0L;

    @TableField("total_input_tokens")
    private Long totalInputTokens = 0L;

    @TableField("total_output_tokens")
    private Long totalOutputTokens = 0L;

    @TableField("total_cost")
    private BigDecimal totalCost = BigDecimal.ZERO;

    private String status = "active";

    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
