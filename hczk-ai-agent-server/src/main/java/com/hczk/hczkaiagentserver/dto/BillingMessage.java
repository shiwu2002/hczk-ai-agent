package com.hczk.hczkaiagentserver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 计费消息对象
 * 
 * 用于异步计费场景，通过RabbitMQ传递计费数据
 * 
 * @author system
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingMessage {

    /** 用户ID */
    private Long userId;

    /** API Key ID（可为null） */
    private Long apiKeyId;

    /** 计费金额（正数为扣费，零为仅记录用量） */
    private BigDecimal amount;

    /** 输入Token数 */
    private Long inputTokens;

    /** 输出Token数 */
    private Long outputTokens;

    /** 计费详情描述 */
    private String detail;

    /** 模型ID */
    private Long modelId;

    /** 模型名称 */
    private String modelName;

    /** 请求ID（用于日志追踪） */
    private String requestId;

    /** 消息时间戳 */
    private long timestamp;

    /**
     * 创建Builder并自动设置requestId和timestamp
     * 
     * @return BillingMessageBuilder
     */
    public static BillingMessageBuilder builder() {
        return new BillingMessageBuilder()
                .timestamp(System.currentTimeMillis())
                .requestId(java.util.UUID.randomUUID().toString());
    }
}