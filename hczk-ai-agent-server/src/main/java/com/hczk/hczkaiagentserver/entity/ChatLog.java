package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对话日志实体
 * 对应 chat_logs 表，记录每次AI对话的输入输出、Token用量和费用
 */
@TableName("chat_logs")
@Data
public class ChatLog {
    /** 主键ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    @TableField("user_id")
    private String userId;

    /** 使用的API Key ID */
    @TableField("api_key_id")
    private Long apiKeyId;

    /** 使用的模型ID */
    @TableField("model_id")
    private Long modelId;

    /** 模型名称（冗余存储，避免关联查询） */
    @TableField("model_name")
    private String modelName;

    /** 用户输入内容 */
    @TableField("input_content")
    private String inputContent;

    /** 模型输出内容 */
    @TableField("output_content")
    private String outputContent;

    /** 输入Token数 */
    @TableField("input_tokens")
    private Long inputTokens;

    /** 输出Token数 */
    @TableField("output_tokens")
    private Long outputTokens;

    /** 本次对话费用（元） */
    private BigDecimal cost;

    /** 响应耗时（毫秒） */
    @TableField("duration_ms")
    private Long durationMs;

    /** 对话状态（success/error） */
    private String status;

    /** 错误信息（失败时记录） */
    @TableField("error_message")
    private String errorMessage;

    /** 创建时间，插入时自动填充 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
