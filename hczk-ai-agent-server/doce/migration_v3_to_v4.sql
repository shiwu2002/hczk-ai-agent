-- ============================================================
-- 数据库迁移脚本：绑定表增加 API Key 和用户关联字段
-- 版本：V3 -> V4
-- 日期：2026-06-16
-- 说明：在 merchant_agent_binding 表中增加 api_key_id、api_key、user_id 字段，
--       支持绑定用户时关联 API Key，调用智能体时自动传递密钥
-- 特性：支持重复执行，已执行的步骤会自动跳过
-- ============================================================

USE hczk_ai_platform;

-- 1. 新增 api_key_id 字段
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'merchant_agent_binding' AND COLUMN_NAME = 'api_key_id');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE merchant_agent_binding ADD COLUMN api_key_id BIGINT COMMENT ''绑定的 API Key ID（关联 api_keys 表）'' AFTER agent_auth_header',
    'SELECT ''api_key_id 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 新增 api_key 字段
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'merchant_agent_binding' AND COLUMN_NAME = 'api_key');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE merchant_agent_binding ADD COLUMN api_key VARCHAR(256) COMMENT ''绑定的 API Key 值（调用智能体时自动传递）'' AFTER api_key_id',
    'SELECT ''api_key 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 新增 user_id 字段
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'merchant_agent_binding' AND COLUMN_NAME = 'user_id');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE merchant_agent_binding ADD COLUMN user_id BIGINT COMMENT ''绑定的用户 ID（关联 users 表）'' AFTER api_key',
    'SELECT ''user_id 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. 新增索引
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'merchant_agent_binding' AND INDEX_NAME = 'idx_user_id');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE merchant_agent_binding ADD KEY idx_user_id (user_id)',
    'SELECT ''idx_user_id 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'merchant_agent_binding' AND INDEX_NAME = 'idx_api_key_id');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE merchant_agent_binding ADD KEY idx_api_key_id (api_key_id)',
    'SELECT ''idx_api_key_id 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 迁移完成验证
-- ============================================================

SELECT '=== 验证 merchant_agent_binding 表结构 ===' AS info;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'hczk_ai_platform'
  AND TABLE_NAME = 'merchant_agent_binding'
ORDER BY ORDINAL_POSITION;
