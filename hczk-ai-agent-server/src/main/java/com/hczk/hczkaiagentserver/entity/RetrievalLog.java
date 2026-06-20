package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库检索日志实体
 * 对应 retrieval_logs 表，记录每次 RAG 检索的查询、命中情况、策略和溯源信息
 */
@TableName("retrieval_logs")
@Data
public class RetrievalLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("agent_id")
    private String agentId;

    @TableField("collection_name")
    private String collectionName;

    private String query;

    private String strategy;

    @TableField("hit_success")
    private Boolean hitSuccess;

    @TableField("hit_count")
    private Integer hitCount;

    @TableField("top_k")
    private Integer topK;

    @TableField("top_score")
    private Double topScore;

    /** 最低得分 */
    @TableField("min_score")
    private Double minScore;

    /** 平均得分 */
    @TableField("avg_score")
    private Double avgScore;

    private Double confidence;

    @TableField("fallback_used")
    private Boolean fallbackUsed;

    /** 命中块溯源摘要 JSON */
    @TableField("hit_sources")
    private String hitSources;

    /** 命中块类型分布 JSON，如 {"prose":3,"qa":1,"table":1} */
    @TableField("chunk_type_dist")
    private String chunkTypeDist;

    /** 查询与命中块的平均向量距离 */
    @TableField("avg_distance")
    private Double avgDistance;

    /** 用户是否采纳（NULL=未反馈/1=采纳/0=拒绝） */
    private Boolean adopted;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
