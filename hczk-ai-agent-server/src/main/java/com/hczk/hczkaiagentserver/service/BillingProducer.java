package com.hczk.hczkaiagentserver.service;

import com.hczk.hczkaiagentserver.config.RabbitMQConfig;
import com.hczk.hczkaiagentserver.dto.BillingMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 计费消息生产者
 *
 * 负责将计费消息发送到RabbitMQ队列，实现异步计费
 *
 * @author system
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送计费消息（扣费）
     *
     * @param userId       用户ID
     * @param apiKeyId     API Key ID
     * @param amount       扣费金额
     * @param inputTokens  输入Token数
     * @param outputTokens 输出Token数
     * @param detail       详情描述
     * @throws RuntimeException 发送失败时抛出异常
     */
    public void sendBillingMessage(Long userId, Long apiKeyId, BigDecimal amount,
                                   Long inputTokens, Long outputTokens, String detail) {
        try {
            BillingMessage message = BillingMessage.builder()
                    .userId(userId)
                    .apiKeyId(apiKeyId)
                    .amount(amount)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .detail(detail)
                    .build();

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.BILLING_EXCHANGE,
                    RabbitMQConfig.BILLING_ROUTING_KEY,
                    message
            );

            log.debug("计费消息发送成功: requestId={}, userId={}, amount={}",
                    message.getRequestId(), userId, amount);
        } catch (Exception e) {
            log.error("计费消息发送失败: userId={}, amount={}, error={}", userId, amount, e.getMessage());
            throw new RuntimeException("计费消息发送失败", e);
        }
    }

    /**
     * 发送用量记录消息（不扣费）
     * 当模型未定价或费用为0时使用
     *
     * @param userId       用户ID
     * @param apiKeyId     API Key ID
     * @param inputTokens  输入Token数
     * @param outputTokens 输出Token数
     * @param detail       详情描述
     */
    public void sendRecordUsageMessage(Long userId, Long apiKeyId,
                                       Long inputTokens, Long outputTokens, String detail) {
        try {
            BillingMessage message = BillingMessage.builder()
                    .userId(userId)
                    .apiKeyId(apiKeyId)
                    .amount(BigDecimal.ZERO)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .detail(detail)
                    .build();

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.BILLING_EXCHANGE,
                    RabbitMQConfig.BILLING_ROUTING_KEY,
                    message
            );

            log.debug("用量记录消息发送成功: requestId={}, userId={}", message.getRequestId(), userId);
        } catch (Exception e) {
            log.error("用量记录消息发送失败: userId={}, error={}", userId, e.getMessage());
        }
    }
}
