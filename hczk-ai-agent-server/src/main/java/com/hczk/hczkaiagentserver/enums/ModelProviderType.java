package com.hczk.hczkaiagentserver.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 模型接口类型枚举
 * OPENAI_COMPATIBLE - OpenAI兼容接口
 * ANTHROPIC - Anthropic接口
 * MODELSCOPE - ModelScope接口
 */
@Getter
@RequiredArgsConstructor
public enum ModelProviderType {
    OPENAI_COMPATIBLE("OPENAI_COMPATIBLE"),
    ANTHROPIC("ANTHROPIC"),
    MODELSCOPE("MODELSCOPE");

    /** 数据库存储值，同时作为JSON序列化值 */
    @EnumValue
    @JsonValue
    private final String value;
}
