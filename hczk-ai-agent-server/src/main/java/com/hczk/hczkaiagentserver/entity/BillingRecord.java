package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.hczk.hczkaiagentserver.enums.BillingType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("billing_records")
@Data
public class BillingRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private String userId;

    @TableField("api_key_id")
    private Long apiKeyId;

    /** 调用的模型ID */
    @TableField("model_id")
    private Long modelId;

    private BillingType type;

    private BigDecimal amount;

    @TableField("balance_after")
    private BigDecimal balanceAfter;

    @TableField("input_tokens")
    private Long inputTokens;

    @TableField("output_tokens")
    private Long outputTokens;

    private String detail;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
