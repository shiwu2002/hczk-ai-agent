package com.hczk.hczkaiagentserver.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户数据传输对象
 * 用于向前端返回用户信息（不包含密码等敏感字段）
 */
@Data
public class UserDTO {
    /** 用户唯一标识（雪花ID） */
    private String userId;
    /** 用户名 */
    private String username;
    /** 邮箱 */
    private String email;
    /** 角色：0=管理员(ADMIN) / 1=普通用户(USER) */
    private Integer role;
    /** 账户余额（元） */
    private BigDecimal balance;
    /** 手机号 */
    private String phoneNumber;
    /** 公司名称 */
    private String companyName;
    /** 账号状态：0正常 / 1禁用 */
    private Integer status;
    /** 注册时间 */
    private LocalDateTime createdAt;
}
