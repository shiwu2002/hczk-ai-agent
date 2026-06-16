package com.hczk.hczkaiagentserver.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.BillingRecord;
import com.hczk.hczkaiagentserver.entity.RechargeRecord;
import com.hczk.hczkaiagentserver.entity.User;
import com.hczk.hczkaiagentserver.enums.BillingType;
import com.hczk.hczkaiagentserver.mapper.BillingRecordMapper;
import com.hczk.hczkaiagentserver.mapper.UserMapper;
import com.hczk.hczkaiagentserver.service.AlipayService;
import com.hczk.hczkaiagentserver.service.BillingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 计费管理控制器
 * 提供账单查询、充值（人工/支付宝）、余额查询等接口
 * 支持管理员操作和用户自助操作两种模式
 */
@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BillingController {

    private final BillingService billingService;
    private final BillingRecordMapper billingRecordMapper;
    private final UserMapper userMapper;
    private final AlipayService alipayService;

    /** 查询指定用户的账单记录（管理员） */
    @GetMapping("/records/{userId}")
    public Result<List<BillingRecord>> getUserBillingRecords(@PathVariable String userId) {
        return Result.success(billingService.getUserBillingRecords(userId));
    }

    /** 查询所有用户的账单记录（管理员） */
    @GetMapping("/records")
    public Result<List<BillingRecord>> getAllBillingRecords() {
        return Result.success(billingService.getAllBillingRecords());
    }

    /** 管理员给用户充值 */
    @PostMapping("/recharge")
    public Result<RechargeRecord> recharge(@RequestParam String userId,
                                           @RequestParam BigDecimal amount,
                                           @RequestParam String paymentMethod) {
        return Result.success(billingService.recharge(userId, amount, paymentMethod));
    }

    /** 查询用户余额 */
    @GetMapping("/balance/{userId}")
    public Result<BigDecimal> getUserBalance(@PathVariable String userId) {
        return Result.success(billingService.getUserBalance(userId));
    }

    /** 用户自助充值（从JWT获取userId） */
    @PostMapping("/self-recharge")
    public Result<RechargeRecord> selfRecharge(@RequestParam BigDecimal amount,
                                               @RequestParam String paymentMethod) {
        String userId = resolveCurrentUserId();
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(billingService.recharge(userId, amount, paymentMethod));
    }

    /** 用户查询自己的全部账单明细 */
    @GetMapping("/my-records")
    public Result<List<BillingRecord>> myRecords() {
        String userId = resolveCurrentUserId();
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(billingService.getUserBillingRecords(userId));
    }

    /** 用户查询自己的扣费明细（仅TOKEN_USAGE类型） */
    @GetMapping("/my-usage")
    public Result<List<BillingRecord>> myUsage() {
        String userId = resolveCurrentUserId();
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

    /** 用户查询自己的充值明细（仅RECHARGE类型） */
    @GetMapping("/my-recharges")
    public Result<List<BillingRecord>> myRecharges() {
        String userId = resolveCurrentUserId();
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
     * 发起支付宝充值
     * 返回支付表单HTML，前端渲染后弹出支付宝支付页面
     */
    @PostMapping("/recharge-alipay")
    public Result<Map<String, String>> createAlipayRecharge(@RequestBody Map<String, Object> body) {
        String userId = resolveCurrentUserId();
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        if (amount.compareTo(new BigDecimal("0.01")) < 0) {
            return Result.error(400, "最低充值金额为0.01元");
        }
        String orderNo = "RCH" + System.currentTimeMillis() + "U" + userId;
        String payForm = alipayService.createOrder(userId, amount, orderNo);
        return Result.success(Map.of("orderNo", orderNo, "payForm", payForm));
    }

    /**
     * 支付宝异步通知回调（无需认证，支付宝服务器调用）
     * 支付成功后支付宝会POST此地址通知支付结果
     */
    @PostMapping("/alipay/notify")
    public String alipayNotify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> params.put(key, values[0]));
        boolean success = alipayService.handleNotify(params);
        return success ? "success" : "failure";
    }

    /** 查询充值订单支付状态（前端轮询） */
    @GetMapping("/recharge/{orderNo}/status")
    public Result<Map<String, Object>> checkRechargeStatus(@PathVariable String orderNo) {
        boolean paid = alipayService.queryOrderStatus(orderNo);
        return Result.success(Map.of("orderNo", orderNo, "paid", paid));
    }

    /**
     * 从Security上下文解析当前用户ID
     * 兼容API Key认证（details中取userId）和JWT认证（principal中取username再查库）
     */
    private String resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;

        // API Key 认证
        if (auth.getDetails() instanceof Map) {
            Map<?, ?> details = (Map<?, ?>) auth.getDetails();
            Object userId = details.get("userId");
            if (userId instanceof String) return (String) userId;
        }

        // JWT 认证 - principal 是 username
        Object principal = auth.getPrincipal();
        if (principal instanceof String) {
            String username = (String) principal;
            User user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            return user != null ? user.getUserId() : null;
        }
        return null;
    }
}
