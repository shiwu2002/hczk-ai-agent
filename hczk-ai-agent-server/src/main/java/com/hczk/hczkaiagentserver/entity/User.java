package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.hczk.hczkaiagentserver.enums.UserRole;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("users")
@Data
public class User {
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户唯一标识（注册时随机生成，如 U1A2B3C4D5E6F7G8） */
    @TableField("user_id")
    private String userId;

    /** 用户名 */
    private String username;

    /** 密码（BCrypt加密存储） */
    private String password;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    @TableField("phone_number")
    private String phoneNumber;

    /** 公司名称 */
    @TableField("company_name")
    private String companyName;

    /** 角色：0管理员(ADMIN) / 1普通用户(USER) */
    private UserRole role = UserRole.USER;

    /** 账户余额（元） */
    private BigDecimal balance = BigDecimal.ZERO;

    /** 累计使用Token数 */
    @TableField("total_usage_tokens")
    private Long totalUsageTokens = 0L;

    /** 账号状态：0正常 / 1禁用 */
    private Integer status = 0;

    /** 逻辑删除：0未删除 / 1已删除 */
    @TableField("deleted")
    @TableLogic
    private Integer deleted = 0;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
