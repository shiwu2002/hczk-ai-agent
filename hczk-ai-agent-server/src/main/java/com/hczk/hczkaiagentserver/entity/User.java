package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.hczk.hczkaiagentserver.enums.UserRole;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("users")
@Data
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String email;

    @TableField("phone_number")
    private String phoneNumber;

    @TableField("company_name")
    private String companyName;

    private UserRole role = UserRole.USER;

    private BigDecimal balance = BigDecimal.ZERO;

    @TableField("total_usage_tokens")
    private Long totalUsageTokens = 0L;

    private String status = "active";

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
