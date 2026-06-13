package com.hczk.hczkaiagentserver.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PlatformType {
    MEITUAN("MEITUAN"),
    DOUYIN("DOUYIN");

    @EnumValue
    private final String value;
}
