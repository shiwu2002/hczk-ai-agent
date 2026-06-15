package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("knowledge_bases")
@Data
public class KnowledgeBase {
    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识库名称 */
    private String name;

    /** 知识库描述 */
    private String description;

    /** 归属类型：AGENT-智能体 / USER-用户 */
    @TableField("owner_type")
    private String ownerType;

    /** 归属者ID */
    @TableField("owner_id")
    private Long ownerId;

    /** Milvus中的agentId标识（如 agent_1、user_2） */
    @TableField("agent_id")
    private String agentId;

    /** Milvus集合名称 */
    @TableField("collection_name")
    private String collectionName = "default";

    /** 向量行数 */
    @TableField("row_count")
    private Long rowCount = 0L;

    /** 状态：0正常 / 1禁用 */
    private Integer status = 0;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
