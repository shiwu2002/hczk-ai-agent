package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务健康指标实体
 * 对应 service_health_metrics 表，记录 Embedder/Milvus/OCR/LLM预处理 等服务的调用健康度
 */
@TableName("service_health_metrics")
@Data
public class ServiceHealthMetric {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("service_name")
    private String serviceName;

    private String operation;

    private Boolean success;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("error_type")
    private String errorType;

    private String detail;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
