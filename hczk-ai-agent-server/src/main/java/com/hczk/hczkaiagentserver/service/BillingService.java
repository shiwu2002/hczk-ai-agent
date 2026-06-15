package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.entity.BillingRecord;
import com.hczk.hczkaiagentserver.entity.RechargeRecord;

import java.math.BigDecimal;
import java.util.List;

/**
 * 计费服务接口
 * 
 * 提供用户余额管理、充值、扣费、账单查询等核心计费功能。
 * 支持同步扣费和异步扣费两种模式，异步扣费通过 RabbitMQ 消息队列实现。
 * 所有模型调用必须计费，不允许"仅记录用量不扣费"的情况。
 * 
 * @author system
 * @since 1.0.0
 */
public interface BillingService {

    /**
     * 获取用户账单记录列表
     * 
     * @param userId 用户ID
     * @return 账单记录列表，按创建时间倒序排列
     */
    List<BillingRecord> getUserBillingRecords(Long userId);

    /**
     * 获取所有账单记录（管理员权限）
     * 
     * @return 所有账单记录列表
     */
    List<BillingRecord> getAllBillingRecords();

    /**
     * 用户充值
     * 
     * @param userId       用户ID
     * @param amount       充值金额
     * @param paymentMethod 支付方式（alipay/wechat/admin等）
     * @return 充值记录实体
     */
    RechargeRecord recharge(Long userId, BigDecimal amount, String paymentMethod);

    /**
     * 同步扣减用户余额
     *
     * 在聊天完成后同步扣减用户余额，适用于需要即时反馈余额的场景。
     * 优先从 Redis 缓存读取余额进行预检查，然后更新数据库。
     *
     * @param userId       用户ID
     * @param apiKeyId     API Key ID
     * @param modelId      调用的模型ID
     * @param amount       扣减金额
     * @param inputTokens  输入Token数
     * @param outputTokens 输出Token数
     * @param detail       扣费详情描述
     * @return 扣费是否成功（余额不足返回false）
     */
    boolean deductBalance(Long userId, Long apiKeyId, Long modelId, BigDecimal amount, Long inputTokens, Long outputTokens, String detail);

    /**
     * 异步扣减余额
     *
     * 由 RabbitMQ 消费者调用，实现异步计费，不阻塞主业务流程。
     *
     * @param userId       用户ID
     * @param apiKeyId     API Key ID
     * @param modelId      调用的模型ID
     * @param amount       扣减金额
     * @param inputTokens  输入Token数
     * @param outputTokens 输出Token数
     * @param detail       扣费详情描述
     */
    void deductBalanceAsync(Long userId, Long apiKeyId, Long modelId, BigDecimal amount, Long inputTokens, Long outputTokens, String detail);

    /**
     * 获取用户余额（从数据库）
     * 
     * @param userId 用户ID
     * @return 用户当前余额
     */
    BigDecimal getUserBalance(Long userId);

    /**
     * 从缓存获取用户余额
     * 
     * 优先从 Redis 缓存获取，缓存不存在时查询数据库并更新缓存。
     * 适用于高频查询场景，减少数据库压力。
     * 
     * @param userId 用户ID
     * @return 用户余额（缓存或数据库查询结果）
     */
    BigDecimal getUserBalanceFromCache(Long userId);
}