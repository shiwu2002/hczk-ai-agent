-- =============================================================
-- 迁移脚本：v5 → v6 — 移除 agents 表的 auth_header 列
-- 日期：2026-06-17
-- 说明：智能体认证已统一使用 JWT，不再需要 auth_header 明文存储密钥
-- =============================================================

-- 检查列是否存在，存在则删除
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'agents'
      AND COLUMN_NAME = 'auth_header');

SET @sql = IF(@col_exists > 0,
    'ALTER TABLE agents DROP COLUMN auth_header',
    'SELECT ''auth_header 列不存在，跳过删除'' AS message');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
