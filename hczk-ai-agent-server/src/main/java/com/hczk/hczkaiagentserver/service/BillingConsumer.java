package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.config.RabbitMQConfig;
import com.hczk.hczkaiagentserver.dto.BillingMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 计费消息消费者
 * 
 * 监听RabbitMQ队列，异步处理计费消息
 * 
 * 处理逻辑：
 * - 所有消息必须包含有效金额（>0），否则记录错误日志
 * - 执行异步扣费操作
 * 
 * @author system
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingConsumer {

    private final BillingService billingService;

    /**
     * 处理计费消息
     * 
     * 使用@RabbitListener注解监听billing.queue队列
     * 使用@Transactional保证数据一致性
     * 
     * @param message 计费消息
     */
    @RabbitListener(queues = RabbitMQConfig.BILLING_QUEUE)
    @Transactional
    public void processBillingMessage(BillingMessage message) {
        try {
            log.debug("收到计费消息: requestId={}, userId={}, amount={}",
                    message.getRequestId(), message.getUserId(), message.getAmount());

            if (message.getAmount() == null || message.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                log.error("计费消息金额无效: requestId={}, userId={}, amount={}",
                        message.getRequestId(), message.getUserId(), message.getAmount());
                return;
            }

            billingService.deductBalanceAsync(
                    message.getUserId(),
                    message.getApiKeyId(),
                    message.getModelId(),
                    message.getAmount(),
                    message.getInputTokens(),
                    message.getOutputTokens(),
                    message.getDetail()
            );

            log.info("计费消息处理完成: requestId={}, userId={}", message.getRequestId(), message.getUserId());
        } catch (Exception e) {
            log.error("计费消息处理失败: requestId={}, userId={}, error={}",
                    message.getRequestId(), message.getUserId(), e.getMessage());
            throw e;
        }
    }
}