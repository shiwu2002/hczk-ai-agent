package com.hczk.hczkaiagentserver.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.BillingRecord;
import com.hczk.hczkaiagentserver.entity.RechargeRecord;
import com.hczk.hczkaiagentserver.entity.User;
import com.hczk.hczkaiagentserver.enums.BillingType;
import com.hczk.hczkaiagentserver.mapper.BillingRecordMapper;
import com.hczk.hczkaiagentserver.mapper.UserMapper;
import com.hczk.hczkaiagentserver.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BillingController {

    private final BillingService billingService;
    private final BillingRecordMapper billingRecordMapper;
    private final UserMapper userMapper;

    @GetMapping("/records/{userId}")
    public Result<List<BillingRecord>> getUserBillingRecords(@PathVariable Long userId) {
        return Result.success(billingService.getUserBillingRecords(userId));
    }

    @GetMapping("/records")
    public Result<List<BillingRecord>> getAllBillingRecords() {
        return Result.success(billingService.getAllBillingRecords());
    }

    /**
     * 管理员给用户充值
     */
    @PostMapping("/recharge")
    public Result<RechargeRecord> recharge(@RequestParam Long userId,
                                           @RequestParam BigDecimal amount,
                                           @RequestParam String paymentMethod) {
        return Result.success(billingService.recharge(userId, amount, paymentMethod));
    }

    @GetMapping("/balance/{userId}")
    public Result<BigDecimal> getUserBalance(@PathVariable Long userId) {
        return Result.success(billingService.getUserBalance(userId));
    }

    /**
     * 用户自己充值（从 JWT 获取 userId）
     */
    @PostMapping("/self-recharge")
    public Result<RechargeRecord> selfRecharge(@RequestParam BigDecimal amount,
                                               @RequestParam String paymentMethod) {
        Long userId = resolveCurrentUserId();
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(billingService.recharge(userId, amount, paymentMethod));
    }

    /**
     * 用户查询自己的账单明细
     */
    @GetMapping("/my-records")
    public Result<List<BillingRecord>> myRecords() {
        Long userId = resolveCurrentUserId();
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(billingService.getUserBillingRecords(userId));
    }

    /**
     * 用户查询自己的扣费明细（仅 TOKEN_USAGE 类型）
     */
    @GetMapping("/my-usage")
    public Result<List<BillingRecord>> myUsage() {
        Long userId = resolveCurrentUserId();
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        List<BillingRecord> records = billingRecordMapper.selectList(
                new LambdaQueryWrapper<BillingRecord>()
                        .eq(BillingRecord::getUserId, userId)
                        .eq(BillingRecord::getType, BillingType.TOKEN_USAGE)
                        .orderByDesc(BillingRecord::getCreatedAt));
        return Result.success(records);
    }

    /**
     * 用户查询自己的充值明细（仅 RECHARGE 类型）
     */
    @GetMapping("/my-recharges")
    public Result<List<BillingRecord>> myRecharges() {
        Long userId = resolveCurrentUserId();
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        List<BillingRecord> records = billingRecordMapper.selectList(
                new LambdaQueryWrapper<BillingRecord>()
                        .eq(BillingRecord::getUserId, userId)
                        .eq(BillingRecord::getType, BillingType.RECHARGE)
                        .orderByDesc(BillingRecord::getCreatedAt));
        return Result.success(records);
    }

    /**
     * 从 Security 上下文解析当前用户 ID
     */
    private Long resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;

        // API Key 认证
        if (auth.getDetails() instanceof Map) {
            Map<?, ?> details = (Map<?, ?>) auth.getDetails();
            Object userId = details.get("userId");
            if (userId instanceof Long) return (Long) userId;
        }

        // JWT 认证 - principal 是 username
        Object principal = auth.getPrincipal();
        if (principal instanceof String) {
            String username = (String) principal;
            User user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            return user != null ? user.getId() : null;
        }
        return null;
    }
}
