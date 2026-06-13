package com.hczk.hczkaiagentserver.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ModelProviderType {
    OPENAI_COMPATIBLE("OPENAI_COMPATIBLE"),
    ANTHROPIC("ANTHROPIC"),
    MODELSCOPE("MODELSCOPE");

    @EnumValue
    private final String value;
}
