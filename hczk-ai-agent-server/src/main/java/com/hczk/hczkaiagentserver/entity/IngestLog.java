package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库入库操作日志实体
 * 对应 ingest_logs 表，记录每次文档入库的操作详情
 */
@TableName("ingest_logs")
@Data
public class IngestLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("agent_id")
    private String agentId;

    @TableField("collection_name")
    private String collectionName;

    private String source;

    @TableField("doc_type")
    private String docType;

    @TableField("file_type")
    private String fileType;

    @TableField("total_pages")
    private Integer totalPages;

    @TableField("total_chunks")
    private Integer totalChunks;

    @TableField("ocr_used")
    private Boolean ocrUsed;

    @TableField("llm_preprocess_used")
    private Boolean llmPreprocessUsed;

    @TableField("llm_preprocess_ms")
    private Long llmPreprocessMs;

    @TableField("ingest_duration_ms")
    private Long ingestDurationMs;

    private String status;

    @TableField("error_message")
    private String errorMessage;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
