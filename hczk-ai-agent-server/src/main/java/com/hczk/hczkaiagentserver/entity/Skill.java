package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工具组实体（v5 重构：从"技能包"改为"工具组/目录"）
 * 每个 Skill 是一个工具组，下面包含多个 ToolDefinition
 * 例如："知识库"组下面有 knowledge_search、knowledge_ingest 等工具
 */
@TableName("skill")
@Data
public class Skill {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 工具组唯一标识（如 knowledge、utility） */
    private String name;

    /** 工具组显示名称（如"知识库"） */
    @TableField("display_name")
    private String displayName;

    /** 分类：knowledge / utility / custom */
    private String category;

    /** 前端图标名称（Lucide icon name，如 Database、Wrench） */
    private String icon;

    /** 版本号 */
    private String version = "1.0.0";

    /** 工具组描述 */
    private String description;

    /** 状态：active / inactive */
    private String status = "active";

    /**
     * 可见性：public 公开 / private 私有（需绑定用户）
     * - public  : 所有智能体可查看和调用
     * - private : 仅被绑定的用户对应的智能体可查看和调用
     */
    private String visibility = "public";

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ===== 非数据库字段，用于前端展示 =====

    /** 该工具组下的工具数量（Transient，由 Service 填充） */
    @TableField(exist = false)
    private Integer toolCount;

    /** 当前用户是否已绑定该工具组（Transient，由 Service 填充，仅私有 skill 有意义） */
    @TableField(exist = false)
    private Boolean bound;
}
