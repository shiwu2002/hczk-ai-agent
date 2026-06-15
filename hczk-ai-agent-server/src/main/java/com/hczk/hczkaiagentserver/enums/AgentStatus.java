package com.hczk.hczkaiagentserver.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 智能体状态枚举
 * 0 - 启用(ACTIVE)：智能体可正常使用
 * 1 - 停用(INACTIVE)：智能体不可使用
 */
@Getter
@RequiredArgsConstructor
public enum AgentStatus {
    /** 启用 */
    ACTIVE(0),
    /** 停用 */
    INACTIVE(1);

    /** 数据库存储值（TINYINT），同时作为JSON序列化值 */
    @EnumValue
    @JsonValue
    private final Integer value;
}
