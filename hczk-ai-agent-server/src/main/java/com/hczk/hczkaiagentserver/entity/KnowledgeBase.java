package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("knowledge_bases")
@Data
public class KnowledgeBase {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    @TableField("owner_type")
    private String ownerType;

    @TableField("owner_id")
    private Long ownerId;

    @TableField("agent_id")
    private String agentId;

    @TableField("collection_name")
    private String collectionName = "default";

    @TableField("row_count")
    private Long rowCount = 0L;

    private String status = "ACTIVE";

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
