package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CLI-Anything 工具命令实体
 * 从 SKILL.md 解析入库，每个命令对应一个可执行操作
 */
@TableName("cli_tool_command")
@Data
public class CliToolCommand {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属 CLI 工具名（关联 cli_tool_registry.name） */
    @TableField("cli_name")
    private String cliName;

    /** 命令组（如 auth、asset） */
    @TableField("command_group")
    private String commandGroup;

    /** 命令组描述 */
    @TableField("group_description")
    private String groupDescription;

    /** 命令名（如 login、list） */
    @TableField("command_name")
    private String commandName;

    /** 命令描述 */
    @TableField("command_description")
    private String commandDescription;

    /** 命令选项列表 JSON */
    private String options;

    /** 完整命令（如 cli-anything-jumpserver auth login） */
    @TableField("full_command")
    private String fullCommand;

    /** 生成的 JSON Schema */
    @TableField("input_schema")
    private String inputSchema;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
