-- =============================================================
-- 迁移脚本：v12 → v13 — CLI-Anything 注册表本地存储
-- 日期：2026-06-22
-- 说明：将远程 CLI-Anything 注册表数据入库，支持定时同步、离线展示、本地安装
-- =============================================================

-- =============================================================
-- 1. CLI 工具注册表（从远程 registry.json 同步入库）
-- =============================================================
DROP TABLE IF EXISTS cli_tool_command;
DROP TABLE IF EXISTS cli_tool_registry;

CREATE TABLE cli_tool_registry (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID' PRIMARY KEY,
    name                VARCHAR(128)   NOT NULL COMMENT 'CLI 工具唯一标识（如 jumpserver）',
    display_name        VARCHAR(256)   NULL     COMMENT '显示名称',
    version             VARCHAR(32)    NULL     COMMENT '版本号',
    description         TEXT           NULL     COMMENT '功能描述',
    `requires`          TEXT           NULL     COMMENT '运行依赖说明',
    homepage            VARCHAR(512)   NULL     COMMENT '项目主页',
    source_url          VARCHAR(512)   NULL     COMMENT '源码地址',
    install_cmd         TEXT           NULL     COMMENT 'pip 安装命令',
    entry_point         VARCHAR(256)   NULL     COMMENT 'CLI 入口命令（如 cli-anything-jumpserver）',
    skill_md            VARCHAR(512)   NULL     COMMENT 'SKILL.md 相对路径',
    category            VARCHAR(64)    NULL     COMMENT '分类（devops/database/ai 等）',
    contributors        JSON           NULL     COMMENT '贡献者列表',
    skill_md_content    LONGTEXT       NULL     COMMENT 'SKILL.md 缓存内容（避免重复拉取）',
    -- 同步状态
    sync_status         VARCHAR(20)    NOT NULL DEFAULT 'pending' COMMENT '同步状态：pending/synced/failed',
    last_sync_at        DATETIME       NULL     COMMENT '最近一次同步成功时间',
    sync_error          TEXT           NULL     COMMENT '同步失败原因',
    sync_retry_count    INT            NOT NULL DEFAULT 0 COMMENT '连续同步失败次数',
    -- 安装状态
    install_status      VARCHAR(20)    NOT NULL DEFAULT 'not_installed' COMMENT '安装状态：not_installed/installing/installed/failed',
    local_install_path  VARCHAR(512)   NULL     COMMENT '本地安装路径',
    installed_version   VARCHAR(32)    NULL     COMMENT '已安装版本',
    installed_at        DATETIME       NULL     COMMENT '安装时间',
    install_error       TEXT           NULL     COMMENT '安装失败原因',
    -- 关联的平台 Skill ID（安装后创建）
    platform_skill_id   VARCHAR(64)    NULL     COMMENT '关联的 skill 表 ID',
    -- 时间戳
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name),
    KEY idx_category (category),
    KEY idx_sync_status (sync_status),
    KEY idx_install_status (install_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLI-Anything 工具注册表（从远程同步）';

-- =============================================================
-- 2. CLI 工具命令（从 SKILL.md 解析入库）
-- =============================================================
CREATE TABLE cli_tool_command (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID' PRIMARY KEY,
    cli_name            VARCHAR(128)   NOT NULL COMMENT '所属 CLI 工具名（关联 cli_tool_registry.name）',
    command_group       VARCHAR(128)   NULL     COMMENT '命令组（如 auth、asset）',
    group_description   VARCHAR(512)   NULL     COMMENT '命令组描述',
    command_name        VARCHAR(128)   NOT NULL COMMENT '命令名（如 login、list）',
    command_description TEXT           NULL     COMMENT '命令描述',
    options             JSON           NULL     COMMENT '命令选项列表',
    full_command        VARCHAR(512)   NULL     COMMENT '完整命令（如 cli-anything-jumpserver auth login）',
    input_schema        JSON           NULL     COMMENT '生成的 JSON Schema',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_cli_name (cli_name),
    KEY idx_command_group (cli_name, command_group),
    CONSTRAINT fk_command_cli FOREIGN KEY (cli_name) REFERENCES cli_tool_registry(name) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLI-Anything 工具命令（从 SKILL.md 解析）';
