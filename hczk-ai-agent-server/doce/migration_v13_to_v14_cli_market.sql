-- =============================================================
-- 迁移脚本：v13 → v14 — CLI 工具市场重构
-- 日期：2026-06-22
-- 说明：
--   1. 新增 is_enabled 字段，管理员控制 CLI 工具的市场可见性
--   2. 创建系统 Skill "CLI工具市场"，智能体通过它发现和安装 CLI 工具
--   3. enable/disable 只翻转标记，不再创建/删除 Skill
-- =============================================================

-- =============================================================
-- 1. 新增 is_enabled 字段（管理员启用/禁用控制）— 幂等，可重复执行
-- =============================================================
-- 仅在列不存在时添加
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cli_tool_registry' AND COLUMN_NAME = 'is_enabled');
SET @sql_add_col = IF(@col_exists = 0,
    'ALTER TABLE cli_tool_registry ADD COLUMN is_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''市场启用状态：0=未启用，1=已启用（仅启用的工具对智能体可见）''',
    'SELECT ''Column is_enabled already exists, skipping''');
PREPARE stmt FROM @sql_add_col;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 仅在索引不存在时添加
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cli_tool_registry' AND INDEX_NAME = 'idx_is_enabled');
SET @sql_add_idx = IF(@idx_exists = 0,
    'ALTER TABLE cli_tool_registry ADD INDEX idx_is_enabled (is_enabled)',
    'SELECT ''Index idx_is_enabled already exists, skipping''');
PREPARE stmt FROM @sql_add_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =============================================================
-- 2. 创建系统 Skill "CLI工具市场"（智能体通过此 Skill 发现和安装 CLI 工具）
-- =============================================================
INSERT IGNORE INTO skill (id, name, display_name, category, icon, version, description, status, visibility, created_at, updated_at)
VALUES ('builtin-cli-market',
        'cli-market',
        'CLI工具市场',
        'utility',
        'Terminal',
        '1.0.0',
        'CLI命令行工具市场，提供命令行工具的发现和按需安装能力。可浏览已启用的CLI工具列表，按需安装到本地环境。',
        'active',
        'public',
        NOW(),
        NOW());

-- =============================================================
-- 3. 创建 "cli_tools_list" 工具定义（列出可用 CLI 工具）
-- =============================================================
INSERT IGNORE INTO tool_definition (skill_id, name, display_name, description, input_schema, endpoint, type, status, created_at, updated_at)
VALUES ('builtin-cli-market',
        'cli_tools_list',
        '列出可用CLI工具',
        '获取已启用的CLI命令行工具列表。返回工具名称、显示名称、描述、分类、版本、命令数量等信息。仅返回管理员在CLI市场启用的工具。',
        '{"type":"object","properties":{},"required":[]}',
        NULL,
        'builtin',
        'active',
        NOW(),
        NOW());

-- =============================================================
-- 4. 创建 "cli_tools_install" 工具定义（安装指定 CLI 工具）
-- =============================================================
INSERT IGNORE INTO tool_definition (skill_id, name, display_name, description, input_schema, endpoint, type, status, created_at, updated_at)
VALUES ('builtin-cli-market',
        'cli_tools_install',
        '安装CLI工具',
        '获取指定CLI工具的完整安装元数据（install_cmd、entry_point、commands等）。智能体拿到元数据后自行 pip install 并在本地执行命令。参数cli_name为工具名称（从cli_tools_list获取）。',
        '{"type":"object","properties":{"cli_name":{"type":"string","description":"要安装的CLI工具名称，从cli_tools_list接口获取"}},"required":["cli_name"]}',
        NULL,
        'builtin',
        'active',
        NOW(),
        NOW());
