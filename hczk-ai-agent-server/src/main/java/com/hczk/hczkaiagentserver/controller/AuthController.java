package com.hczk.hczkaiagentserver.controller;

import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.dto.LoginRequest;
import com.hczk.hczkaiagentserver.dto.LoginResponse;
import com.hczk.hczkaiagentserver.dto.RegisterRequest;
import com.hczk.hczkaiagentserver.entity.User;
import com.hczk.hczkaiagentserver.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

    @PostMapping("/register")
    public Result<User> register(@RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }
}
