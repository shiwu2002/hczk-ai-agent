package com.hczk.hczkaiagentserver.dto;

import lombok.Data;

/**
 * 登录响应DTO
 * 登录成功后返回JWT令牌和用户基本信息
 */
@Data
public class LoginResponse {
    /** 访问令牌（JWT） */
    private String token;
    /** 刷新令牌，用于续期访问令牌 */
    private String refreshToken;
    /** 用户基本信息 */
    private UserDTO user;
}
