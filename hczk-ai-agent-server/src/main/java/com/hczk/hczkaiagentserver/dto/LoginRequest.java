package com.hczk.hczkaiagentserver.dto;

import lombok.Data;

/**
 * 登录请求DTO
 * 用于接收用户登录时的用户名和密码
 */
@Data
public class LoginRequest {
    /** 用户名 */
    private String username;
    /** 密码 */
    private String password;
}
