package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("skill")
@Data
public class Skill {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;

    private String category;

    private String version = "1.0.0";

    private String description;

    private String persona;

    private String capabilities;

    private String workflow;

    private String config;

    private String status = "draft";

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}