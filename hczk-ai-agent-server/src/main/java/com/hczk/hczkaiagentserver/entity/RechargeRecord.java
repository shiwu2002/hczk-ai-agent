package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("recharge_records")
@Data
public class RechargeRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private String userId;

    private BigDecimal amount;

    @TableField("bonus_amount")
    private BigDecimal bonusAmount = BigDecimal.ZERO;

    @TableField("payment_method")
    private String paymentMethod;

    @TableField("transaction_id")
    private String transactionId;

    private String status = "success";

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
