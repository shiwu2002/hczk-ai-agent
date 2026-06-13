package com.hczk.hczkaiagentserver.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BillingType {
    TOKEN_USAGE("TOKEN_USAGE"),
    RECHARGE("RECHARGE");

    @EnumValue
    private final String value;
}
