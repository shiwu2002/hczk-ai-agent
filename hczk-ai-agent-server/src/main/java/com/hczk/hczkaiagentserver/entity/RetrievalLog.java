package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库检索日志实体
 * 对应 retrieval_logs 表，记录每次 RAG 检索的查询、命中情况、策略和溯源信息
 * 用于管理员监控检索命中率变化和检索质量
 */
@TableName("retrieval_logs")
@Data
public class RetrievalLog {
    /** 主键ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 智能体ID（即用户ID） */
    @TableField("agent_id")
    private String agentId;

    /** 集合名 */
    @TableField("collection_name")
    private String collectionName;

    /** 检索查询文本 */
    private String query;

    /** 检索策略（hybrid/vector/keyword/fallback） */
    private String strategy;

    /** 是否命中有效结果（results 非空且非 fallback） */
    @TableField("hit_success")
    private Boolean hitSuccess;

    /** 命中结果数量 */
    @TableField("hit_count")
    private Integer hitCount;

    /** 请求的 topK */
    @TableField("top_k")
    private Integer topK;

    /** 最高得分 */
    @TableField("top_score")
    private Double topScore;

    /** 置信度 */
    private Double confidence;

    /** 是否降级到兜底回复 */
    @TableField("fallback_used")
    private Boolean fallbackUsed;

    /** 命中块的溯源摘要（JSON：页码、章节、源文件） */
    @TableField("hit_sources")
    private String hitSources;

    /** 检索耗时（毫秒） */
    @TableField("duration_ms")
    private Long durationMs;

    /** 创建时间，插入时自动填充 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
