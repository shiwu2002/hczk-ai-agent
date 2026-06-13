package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.hczk.hczkaiagentserver.enums.ModelProviderType;
import com.hczk.hczkaiagentserver.enums.ModelStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ai_models")
@Data
public class AiModel {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String provider;

    @TableField("provider_type")
    private ModelProviderType providerType = ModelProviderType.OPENAI_COMPATIBLE;

    @TableField("model_id")
    private String modelId;

    private ModelStatus status = ModelStatus.ACTIVE;

    @TableField("api_base")
    private String apiBase;

    @TableField("api_key")
    private String apiKey;

    @TableField("input_price")
    private BigDecimal inputPrice;

    @TableField("output_price")
    private BigDecimal outputPrice;

    @TableField("max_tokens")
    private Integer maxTokens;

    private Boolean thinking = false;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
