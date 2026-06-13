package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.dto.ForgotPasswordRequest;
import com.hczk.hczkaiagentserver.dto.LoginRequest;
import com.hczk.hczkaiagentserver.dto.LoginResponse;
import com.hczk.hczkaiagentserver.dto.RegisterRequest;
import com.hczk.hczkaiagentserver.entity.User;
import com.hczk.hczkaiagentserver.service.EmailService;
import com.hczk.hczkaiagentserver.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 * 提供用户登录、注册和验证码发送接口
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserService userService;
    private final EmailService emailService;

    /**
     * 用户登录
     *
     * @param request 登录请求（包含用户名和密码）
     * @return 登录响应（包含JWT令牌和用户信息）
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    /**
     * 用户注册
     *
     * @param request 注册请求（包含用户名、密码、邮箱等）
     * @return 注册成功的用户信息
     */
    @PostMapping("/register")
    public Result<User> register(@RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    /**
     * 发送邮箱验证码
     *
     * @param body 请求体，需包含 email 字段
     * @return 无返回值
     */
    @PostMapping("/send-code")
    public Result<Void> sendCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            throw new RuntimeException("邮箱不能为空");
        }
        emailService.sendVerificationCode(email);
        return Result.success(null);
    }

    /**
     * 忘记密码 - 重置密码
     * 通过邮箱验证码校验后重置用户密码
     *
     * @param request 忘记密码请求（包含邮箱、验证码、新密码）
     * @return 无返回值
     */
    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        userService.resetPassword(request);
        return Result.success(null);
    }
}
