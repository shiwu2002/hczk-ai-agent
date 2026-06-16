package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.dto.*;
import com.hczk.hczkaiagentserver.entity.User;

import java.util.List;

/**
 * 用户服务接口
 * 提供用户登录、注册、查询和更新等业务功能
 */
public interface UserService {

    /**
     * 用户登录
     *
     * @param request 登录请求（包含用户名和密码）
     * @return 登录响应（包含JWT令牌和用户信息）
     */
    LoginResponse login(LoginRequest request);

    /**
     * 用户注册
     *
     * @param request 注册请求（包含用户名、密码、邮箱等）
     * @return 注册成功的用户信息
     */
    User register(RegisterRequest request);

    /**
     * 根据用户名获取当前用户信息
     *
     * @param username 用户名
     * @return 用户DTO信息
     */
    UserDTO getCurrentUser(String username);

    /**
     * 获取所有用户列表
     *
     * @return 用户列表
     */
    List<User> getAllUsers();

    /**
     * 更新用户信息
     *
     * @param userId  用户ID（雪花ID）
     * @param userDTO 用户更新数据
     * @return 更新后的用户信息
     */
    User updateUser(String userId, UserDTO userDTO);

    /**
     * 重置密码（忘记密码）
     * 通过邮箱验证码校验后重置用户密码
     *
     * @param request 忘记密码请求（包含邮箱、验证码、新密码）
     * @throws RuntimeException 验证码错误、邮箱不存在时抛出
     */
    void resetPassword(ForgotPasswordRequest request);
}
