package com.hczk.hczkaiagentserver.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 第三方渠道平台类型枚举
 */
@Getter
@RequiredArgsConstructor
public enum PlatformType {
    MEITUAN("MEITUAN"),
    DOUYIN("DOUYIN");

    /** 数据库存储值，同时作为JSON序列化值 */
    @EnumValue
    @JsonValue
    private final String value;
}
