package com.hczk.hczkaiagentserver.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.dto.UserDTO;
import com.hczk.hczkaiagentserver.entity.ApiKey;
import com.hczk.hczkaiagentserver.entity.BillingRecord;
import com.hczk.hczkaiagentserver.entity.User;
import com.hczk.hczkaiagentserver.enums.BillingType;
import com.hczk.hczkaiagentserver.mapper.ApiKeyMapper;
import com.hczk.hczkaiagentserver.mapper.BillingRecordMapper;
import com.hczk.hczkaiagentserver.service.UserService;
import com.hczk.hczkaiagentserver.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器
 * 提供用户信息查询与更新接口
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final BillingRecordMapper billingRecordMapper;
    private final ApiKeyMapper apiKeyMapper;

    /**
     * 获取当前登录用户信息
     *
     * @param authHeader 请求头中的 Authorization 字段，格式: Bearer {token}
     * @return 当前用户信息
     */
    @GetMapping("/me")
    public Result<UserDTO> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String username = jwtUtil.getUsernameFromToken(token);
        return Result.success(userService.getCurrentUser(username));
    }

    /**
     * 获取所有用户列表
     *
     * @return 用户列表
     */
    @GetMapping
    public Result<List<User>> getAllUsers() {
        return Result.success(userService.getAllUsers());
    }

    /**
     * 更新用户信息
     *
     * @param id      用户ID（雪花ID）
     * @param userDTO 用户更新数据
     * @return 更新后的用户信息
     */
    @PutMapping("/{id}")
    public Result<User> updateUser(@PathVariable String id, @RequestBody UserDTO userDTO) {
        return Result.success(userService.updateUser(id, userDTO));
    }

    /**
     * 获取指定用户的使用明细（单次调用记录）
     */
    @GetMapping("/{id}/usage")
    public Result<List<BillingRecord>> getUserUsage(@PathVariable String id) {
        List<BillingRecord> records = billingRecordMapper.selectList(
                new LambdaQueryWrapper<BillingRecord>()
                        .eq(BillingRecord::getUserId, id)
                        .eq(BillingRecord::getType, BillingType.TOKEN_USAGE)
                        .orderByDesc(BillingRecord::getCreatedAt));
        return Result.success(records);
    }

    /**
     * 获取指定用户的 API Key 列表
     */
    @GetMapping("/{id}/api-keys")
    public Result<List<ApiKey>> getUserApiKeys(@PathVariable String id) {
        List<ApiKey> keys = apiKeyMapper.selectList(
                new LambdaQueryWrapper<ApiKey>()
                        .eq(ApiKey::getUserId, id)
                        .orderByDesc(ApiKey::getCreatedAt));
        return Result.success(keys);
    }
}
