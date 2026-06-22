package com.hczk.hczkaiagentserver.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * CLI-Anything 工具注册表实体
 * 从远程 registry.json 同步入库，支持离线展示和定时同步
 */
@TableName("cli_tool_registry")
@Data
public class CliToolRegistry {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** CLI 工具唯一标识（如 jumpserver） */
    private String name;

    /** 显示名称 */
    @TableField("display_name")
    private String displayName;

    /** 版本号 */
    private String version;

    /** 功能描述 */
    private String description;

    /** 运行依赖说明 */
    @TableField("`requires`")
    private String requires;

    /** 项目主页 */
    private String homepage;

    /** 源码地址 */
    @TableField("source_url")
    private String sourceUrl;

    /** pip 安装命令 */
    @TableField("install_cmd")
    private String installCmd;

    /** CLI 入口命令（如 cli-anything-jumpserver） */
    @TableField("entry_point")
    private String entryPoint;

    /** SKILL.md 相对路径 */
    @TableField("skill_md")
    private String skillMd;

    /** 分类（devops/database/ai 等） */
    private String category;

    /** 贡献者列表 JSON */
    private String contributors;

    /** SKILL.md 缓存内容 */
    @TableField("skill_md_content")
    private String skillMdContent;

    // ===== 市场状态（管理员控制） =====

    /** 市场启用状态（管理员控制，启用后智能体可通过 cli_tools_list 发现该工具） */
    @TableField("is_enabled")
    private Boolean isEnabled;

    // ===== 同步状态 =====

    /** 同步状态：pending/synced/failed */
    @TableField("sync_status")
    private String syncStatus;

    /** 最近一次同步成功时间 */
    @TableField("last_sync_at")
    private LocalDateTime lastSyncAt;

    /** 同步失败原因 */
    @TableField("sync_error")
    private String syncError;

    /** 连续同步失败次数 */
    @TableField("sync_retry_count")
    private Integer syncRetryCount;

    // ===== 安装状态（智能体主动安装后更新） =====
    // 注意：与 isEnabled（管理员市场开关）不同，installStatus 跟踪实际的 pip 安装和 Skill 注册状态

    /** 安装状态：not_installed/installing/installed/failed */
    @TableField("install_status")
    private String installStatus;

    /** 安装时间 */
    @TableField("installed_at")
    private LocalDateTime installedAt;

    /** 安装失败原因 */
    @TableField("install_error")
    private String installError;

    /** 关联的平台 Skill ID（安装后创建） */
    @TableField("platform_skill_id")
    private String platformSkillId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ===== 非数据库字段 =====

    /** 该工具下的命令数量（Transient） */
    @TableField(exist = false)
    private Integer commandCount;
}
