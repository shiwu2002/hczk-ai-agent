package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工具定义实体（v5 新增）
 * 每个 ToolDefinition 定义一个可被智能体调用的工具（function）
 * 归属于某个 Skill（工具组）
 */
@TableName("tool_definition")
@Data
public class ToolDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属工具组ID */
    @TableField("skill_id")
    private String skillId;

    /** 工具名称（function name，如 knowledge_search） */
    private String name;

    /** 工具显示名称（如"知识库检索"） */
    @TableField("display_name")
    private String displayName;

    /** 工具功能描述（传递给 LLM） */
    private String description;

    /**
     * 输入参数 JSON Schema
     * 格式：{"type":"object","properties":{...},"required":[...]}
     */
    @TableField("input_schema")
    private String inputSchema;

    /**
     * 工具执行端点
     * - builtin 类型：为 NULL，由平台内部执行
     * - api 类型：填写外部 HTTP URL
     */
    private String endpoint;

    /**
     * 工具类型：
     * builtin — 内置工具（平台内部执行，如知识库操作）
     * api     — 外部API工具（代理转发到外部端点）
     */
    private String type = "builtin";

    /** 状态：active / inactive */
    private String status = "active";

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
