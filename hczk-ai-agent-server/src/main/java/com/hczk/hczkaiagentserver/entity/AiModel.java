package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.hczk.hczkaiagentserver.enums.ModelProviderType;
import com.hczk.hczkaiagentserver.enums.ModelStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI模型实体
 * 对应 ai_models 表，存储各AI大模型的配置信息
 */
@TableName("ai_models")
@Data
public class AiModel {
    /** 主键ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模型显示名称 */
    private String name;

    /** 模型提供商（如DashScope、OpenAI等） */
    private String provider;

    /** 提供商类型（OPENAI_COMPATIBLE / ANTHROPIC） */
    @TableField("provider_type")
    private ModelProviderType providerType = ModelProviderType.OPENAI_COMPATIBLE;

    /** 模型唯一标识（如qwen3.7-plus、gpt-4o等） */
    @TableField("model_id")
    private String modelId;

    /** 模型状态（ACTIVE启用 / DISABLED禁用） */
    private ModelStatus status = ModelStatus.ACTIVE;

    /** API基础地址 */
    @TableField("api_base")
    private String apiBase;

    /** API密钥 */
    @TableField("api_key")
    private String apiKey;

    /** 最大输出Token数 */
    @TableField("max_tokens")
    private Integer maxTokens;

    /** 是否启用深度思考模式（如qwen3.7-plus的enable_thinking） */
    private Boolean thinking = false;

    /** 创建时间，插入时自动填充 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间，插入和更新时自动填充 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
