package com.hczk.hczkaiagentserver.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 用户角色枚举
 * 0 - 管理员(ADMIN)：拥有系统管理权限
 * 1 - 普通用户(USER)：使用API Key调用模型
 */
@Getter
@RequiredArgsConstructor
public enum UserRole {
    /** 管理员 */
    ADMIN(0),
    /** 普通用户 */
    USER(1);

    /** 数据库存储值（TINYINT），同时作为JSON序列化值 */
    @EnumValue
    @JsonValue
    private final Integer value;
}
