-- =============================================================
-- 迁移脚本：v6 → v7 — 绑定表改用 user_id 替代 merchant_id
-- 日期：2026-06-17
-- 说明：知识库绑定从"绑定商家ID（API Key名称）+ API Key"简化为"只绑定用户ID（雪花ID）"
-- =============================================================

-- =============================================================
-- 0. 安全删除列的存储过程（可重复执行）
-- =============================================================
DROP PROCEDURE IF EXISTS drop_column_if_exists;
DELIMITER //
CREATE PROCEDURE drop_column_if_exists(
    IN tbl_name VARCHAR(128),
    IN col_name VARCHAR(128)
)
BEGIN
    DECLARE col_count INT DEFAULT 0;
    SELECT COUNT(*) INTO col_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = tbl_name
      AND COLUMN_NAME = col_name;
    IF col_count > 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', tbl_name, ' DROP COLUMN ', col_name);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('已删除列: ', col_name) AS msg;
    ELSE
        SELECT CONCAT('列不存在，跳过: ', col_name) AS msg;
    END IF;
END //
DELIMITER ;

-- =============================================================
-- 1. 安全添加列的存储过程（可重复执行）
-- =============================================================
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DELIMITER //
CREATE PROCEDURE add_column_if_not_exists(
    IN tbl_name VARCHAR(128),
    IN col_name VARCHAR(128),
    IN col_def VARCHAR(512),
    IN after_col VARCHAR(128)
)
BEGIN
    DECLARE col_count INT DEFAULT 0;
    SELECT COUNT(*) INTO col_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = tbl_name
      AND COLUMN_NAME = col_name;
    IF col_count = 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', tbl_name, ' ADD COLUMN ', col_name, ' ', col_def, ' AFTER ', after_col);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('已添加列: ', col_name) AS msg;
    ELSE
        SELECT CONCAT('列已存在，跳过: ', col_name) AS msg;
    END IF;
END //
DELIMITER ;

-- =============================================================
-- 2. 添加 user_id 列（如果不存在）
-- =============================================================
CALL add_column_if_not_exists('merchant_agent_binding', 'user_id',
    "BIGINT NULL COMMENT '绑定的用户ID（雪花ID）'", 'id');

-- =============================================================
-- 3. 删除旧索引 uk_merchant
-- =============================================================
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'merchant_agent_binding'
      AND INDEX_NAME = 'uk_merchant');

SET @sql = IF(@idx_exists > 0,
    'ALTER TABLE merchant_agent_binding DROP INDEX uk_merchant',
    'SELECT ''uk_merchant 索引不存在，跳过'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =============================================================
-- 4. 删除旧索引 idx_skill_id
-- =============================================================
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'merchant_agent_binding'
      AND INDEX_NAME = 'idx_skill_id');

SET @sql = IF(@idx_exists > 0,
    'ALTER TABLE merchant_agent_binding DROP INDEX idx_skill_id',
    'SELECT ''idx_skill_id 索引不存在，跳过'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =============================================================
-- 5. 添加 user_id 唯一索引
-- =============================================================
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'merchant_agent_binding'
      AND INDEX_NAME = 'uk_user_id');

SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE merchant_agent_binding ADD UNIQUE KEY uk_user_id (user_id)',
    'SELECT ''uk_user_id 索引已存在，跳过'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =============================================================
-- 6. 删除废弃列：merchant_id, skill_id, api_key_id, api_key
-- =============================================================
CALL drop_column_if_exists('merchant_agent_binding', 'merchant_id');
CALL drop_column_if_exists('merchant_agent_binding', 'skill_id');
CALL drop_column_if_exists('merchant_agent_binding', 'api_key_id');
CALL drop_column_if_exists('merchant_agent_binding', 'api_key');

-- =============================================================
-- 7. 清理存储过程
-- =============================================================
DROP PROCEDURE IF EXISTS drop_column_if_exists;
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
