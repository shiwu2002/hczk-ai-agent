package com.hczk.hczkaiagentserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.entity.ApiKey;
import com.hczk.hczkaiagentserver.entity.BillingRecord;
import com.hczk.hczkaiagentserver.entity.RechargeRecord;
import com.hczk.hczkaiagentserver.entity.User;
import com.hczk.hczkaiagentserver.enums.BillingType;
import com.hczk.hczkaiagentserver.mapper.ApiKeyMapper;
import com.hczk.hczkaiagentserver.mapper.BillingRecordMapper;
import com.hczk.hczkaiagentserver.mapper.RechargeRecordMapper;
import com.hczk.hczkaiagentserver.mapper.UserMapper;
import com.hczk.hczkaiagentserver.service.BillingService;
import com.hczk.hczkaiagentserver.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 计费服务实现类
 * 
 * 提供用户余额管理、充值、扣费、账单查询等核心计费功能。
 * 所有模型调用都必须计费，确保每一次API调用都产生费用。
 * 
 * 主要特性：
 * - Redis缓存用户余额，减少数据库查询压力
 * - 支持同步和异步两种扣费模式
 * - 完整的事务管理确保数据一致性
 * - 自动更新API Key使用统计
 * 
 * @author system
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private final BillingRecordMapper billingRecordMapper;
    private final RechargeRecordMapper rechargeRecordMapper;
    private final UserMapper userMapper;
    private final ApiKeyMapper apiKeyMapper;
    private final RedisCacheService redisCacheService;

    /**
     * 获取用户账单记录列表
     * 
     * @param userId 用户ID
     * @return 账单记录列表，按创建时间倒序排列
     */
    @Override
    public List<BillingRecord> getUserBillingRecords(Long userId) {
        return billingRecordMapper.selectList(
                new LambdaQueryWrapper<BillingRecord>()
                        .eq(BillingRecord::getUserId, userId)
                        .orderByDesc(BillingRecord::getCreatedAt));
    }

    /**
     * 获取所有账单记录（管理员权限）
     * 
     * @return 所有账单记录列表
     */
    @Override
    public List<BillingRecord> getAllBillingRecords() {
        return billingRecordMapper.selectList(null);
    }

    /**
     * 用户充值
     * 
     * 增加用户余额，记录充值记录和账单记录，并失效缓存。
     * 
     * @param userId       用户ID
     * @param amount       充值金额
     * @param paymentMethod 支付方式（alipay/wechat/admin等）
     * @return 充值记录实体
     * @throws RuntimeException 如果用户不存在
     */
    @Override
    @Transactional
    public RechargeRecord recharge(Long userId, BigDecimal amount, String paymentMethod) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        user.setBalance(user.getBalance().add(amount));
        userMapper.updateById(user);

        RechargeRecord record = new RechargeRecord();
        record.setUserId(userId);
        record.setAmount(amount);
        record.setPaymentMethod(paymentMethod);
        record.setTransactionId("TRX" + System.currentTimeMillis());
        rechargeRecordMapper.insert(record);

        BillingRecord billing = new BillingRecord();
        billing.setUserId(userId);
        billing.setType(BillingType.RECHARGE);
        billing.setAmount(amount);
        billing.setBalanceAfter(user.getBalance());
        billing.setDetail(paymentMethod + "充值");
        billingRecordMapper.insert(billing);

        redisCacheService.invalidateUserBalance(userId);

        return record;
    }

    /**
     * 同步扣减用户余额
     *
     * 计费流程：API Key → 查询用户 → 余额检查 → 扣减余额 → 记录账单 → 更新API Key统计
     * 优先从Redis缓存读取余额进行预检查，然后更新数据库。
     *
     * @param userId       用户ID
     * @param apiKeyId     API Key ID
     * @param modelId      调用的模型ID
     * @param amount       扣减金额（由API Key的unitPrice计算得出）
     * @param inputTokens  输入Token数
     * @param outputTokens 输出Token数
     * @param detail       扣费详情描述
     * @return 扣费是否成功（余额不足返回false）
     * @throws RuntimeException 如果用户不存在
     */
    @Override
    @Transactional
    public boolean deductBalance(Long userId, Long apiKeyId, Long modelId, BigDecimal amount, Long inputTokens, Long outputTokens, String detail) {
        BigDecimal cachedBalance = redisCacheService.getUserBalance(userId);
        if (cachedBalance.compareTo(amount) < 0) {
            log.warn("缓存余额不足: userId={}, balance={}, required={}", userId, cachedBalance, amount);
            return false;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (user.getBalance().compareTo(amount) < 0) {
            redisCacheService.invalidateUserBalance(userId);
            return false;
        }

        user.setBalance(user.getBalance().subtract(amount));
        user.setTotalUsageTokens(user.getTotalUsageTokens() + inputTokens + outputTokens);
        userMapper.updateById(user);

        redisCacheService.cacheUserBalance(userId, user.getBalance());

        BillingRecord record = new BillingRecord();
        record.setUserId(userId);
        record.setApiKeyId(apiKeyId);
        record.setModelId(modelId);
        record.setType(BillingType.TOKEN_USAGE);
        record.setAmount(amount.negate());
        record.setBalanceAfter(user.getBalance());
        record.setInputTokens(inputTokens);
        record.setOutputTokens(outputTokens);
        record.setDetail(detail);
        billingRecordMapper.insert(record);

        if (apiKeyId != null) {
            try {
                ApiKey apiKey = apiKeyMapper.selectById(apiKeyId);
                if (apiKey != null) {
                    apiKey.setTotalInputTokens(apiKey.getTotalInputTokens() + inputTokens);
                    apiKey.setTotalOutputTokens(apiKey.getTotalOutputTokens() + outputTokens);
                    apiKey.setTotalCost(apiKey.getTotalCost().add(amount));
                    apiKey.setTotalCalls(apiKey.getTotalCalls() + 1);
                    apiKeyMapper.updateById(apiKey);
                }
            } catch (Exception e) {
                log.warn("更新 API Key 统计失败: apiKeyId={}, error={}", apiKeyId, e.getMessage());
            }
        }

        return true;
    }

    /**
     * 异步扣减余额
     *
     * 由RabbitMQ消费者调用，实现异步计费，不阻塞主业务流程。
     *
     * @param userId       用户ID
     * @param apiKeyId     API Key ID
     * @param modelId      调用的模型ID
     * @param amount       扣减金额
     * @param inputTokens  输入Token数
     * @param outputTokens 输出Token数
     * @param detail       扣费详情描述
     */
    @Override
    @Transactional
    public void deductBalanceAsync(Long userId, Long apiKeyId, Long modelId, BigDecimal amount, Long inputTokens, Long outputTokens, String detail) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.error("异步扣费失败：用户不存在: userId={}", userId);
            return;
        }

        if (user.getBalance().compareTo(amount) < 0) {
            log.warn("异步扣费失败：余额不足: userId={}, balance={}, required={}", userId, user.getBalance(), amount);
            return;
        }

        user.setBalance(user.getBalance().subtract(amount));
        user.setTotalUsageTokens(user.getTotalUsageTokens() + inputTokens + outputTokens);
        userMapper.updateById(user);

        redisCacheService.cacheUserBalance(userId, user.getBalance());

        BillingRecord record = new BillingRecord();
        record.setUserId(userId);
        record.setApiKeyId(apiKeyId);
        record.setModelId(modelId);
        record.setType(BillingType.TOKEN_USAGE);
        record.setAmount(amount.negate());
        record.setBalanceAfter(user.getBalance());
        record.setInputTokens(inputTokens);
        record.setOutputTokens(outputTokens);
        record.setDetail(detail);
        billingRecordMapper.insert(record);

        if (apiKeyId != null) {
            try {
                ApiKey apiKey = apiKeyMapper.selectById(apiKeyId);
                if (apiKey != null) {
                    apiKey.setTotalInputTokens(apiKey.getTotalInputTokens() + inputTokens);
                    apiKey.setTotalOutputTokens(apiKey.getTotalOutputTokens() + outputTokens);
                    apiKey.setTotalCost(apiKey.getTotalCost().add(amount));
                    apiKey.setTotalCalls(apiKey.getTotalCalls() + 1);
                    apiKeyMapper.updateById(apiKey);
                }
            } catch (Exception e) {
                log.warn("更新 API Key 统计失败: apiKeyId={}, error={}", apiKeyId, e.getMessage());
            }
        }
    }

    /**
     * 获取用户余额（从数据库）
     * 
     * @param userId 用户ID
     * @return 用户当前余额
     * @throws RuntimeException 如果用户不存在
     */
    @Override
    public BigDecimal getUserBalance(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return user.getBalance();
    }

    /**
     * 从缓存获取用户余额
     * 
     * 优先从Redis缓存获取，缓存不存在时查询数据库并更新缓存。
     * 适用于高频查询场景，减少数据库压力。
     * 
     * @param userId 用户ID
     * @return 用户余额（缓存或数据库查询结果）
     */
    @Override
    public BigDecimal getUserBalanceFromCache(Long userId) {
        return redisCacheService.getUserBalance(userId);
    }
}