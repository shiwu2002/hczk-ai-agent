package com.hczk.hczkaiagentserver.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ModelStatus {
    ACTIVE("ACTIVE"),
    INACTIVE("INACTIVE");

    @EnumValue
    private final String value;
}
