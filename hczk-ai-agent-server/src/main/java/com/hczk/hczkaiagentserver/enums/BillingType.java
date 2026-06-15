package com.hczk.hczkaiagentserver.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 计费类型枚举
 * TOKEN_USAGE - 模型调用扣费（金额为负）
 * RECHARGE - 充值（金额为正）
 */
@Getter
@RequiredArgsConstructor
public enum BillingType {
    TOKEN_USAGE("TOKEN_USAGE"),
    RECHARGE("RECHARGE");

    /** 数据库存储值，同时作为JSON序列化值 */
    @EnumValue
    @JsonValue
    private final String value;
}
