package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@TableName(value = "api_keys", autoResultMap = true)
@Data
public class ApiKey {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField("api_key")
    private String apiKey;

    @TableField("user_id")
    private Long userId;

    /** 绑定的大模型ID列表（一对多） */
    @TableField(value = "model_ids", typeHandler = JacksonTypeHandler.class)
    private List<Long> modelIds;

    /** 统一Token单价（元/千Tokens），计费依据 */
    @TableField("unit_price")
    private BigDecimal unitPrice = BigDecimal.ZERO;

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
