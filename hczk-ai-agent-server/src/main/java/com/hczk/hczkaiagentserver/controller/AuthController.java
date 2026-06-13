package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.dto.LoginRequest;
import com.hczk.hczkaiagentserver.dto.LoginResponse;
import com.hczk.hczkaiagentserver.dto.RegisterRequest;
import com.hczk.hczkaiagentserver.entity.User;
import com.hczk.hczkaiagentserver.service.EmailService;
import com.hczk.hczkaiagentserver.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserService userService;
    private final EmailService emailService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    @PostMapping("/register")
    public Result<User> register(@RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    @PostMapping("/send-code")
    public Result<Void> sendCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            throw new RuntimeException("邮箱不能为空");
        }
        emailService.sendVerificationCode(email);
        return Result.success(null);
    }
}
