-- =============================================================
-- 迁移脚本：v8 → v9 — users 表主键切换为 user_id（雪花ID）
-- 日期：2026-06-17
-- 说明：删除 users.id（BIGINT 自增主键），将 user_id（VARCHAR(32) 雪花ID）设为主键
--       所有引用 users.id 的子表 user_id 列类型从 BIGINT 改为 VARCHAR(32)
--       并将数据从数字 ID 更新为对应的 user_id 字符串值
--
-- 执行顺序（关键）：
--   1) 删除外键约束（避免后续 MODIFY COLUMN 受阻）
--   2) 修改子表 user_id 列类型 BIGINT → VARCHAR(32)（避免 UPDATE 时类型不匹配报错）
--   3) 数据迁移：UPDATE 子表 user_id（数字）→ users.user_id（字符串）
--   4) 修改 users 表：移除 AUTO_INCREMENT → 删除主键 → 删除 id 列 → user_id 设为主键
--   5) 重建外键约束（引用 users.user_id）
--
-- 兼容性说明：
--   - 不使用存储过程和 DELIMITER，兼容 Navicat / DBeaver / MySQL Workbench 等所有客户端
--   - 不使用 MariaDB 的 IF EXISTS 语法，兼容 MySQL 8.0
--   - 使用 INFORMATION_SCHEMA 查询 + PREPARE stmt 实现幂等
--
-- 注意：执行前请务必备份数据库！
-- =============================================================

USE hczk_ai_platform;

-- 临时禁用外键检查，避免修改表结构时被外键约束阻塞
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================
-- 1. 删除外键约束（引用 users.id）
--    必须先删除外键，否则 MODIFY COLUMN user_id 会因外键列类型
--    必须与引用列类型一致而报错
-- =============================================================

-- 1.1 api_keys.fk_apikey_user
SET @fk_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_keys'
      AND CONSTRAINT_NAME = 'fk_apikey_user' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @sql = IF(@fk_exists > 0,
    'ALTER TABLE api_keys DROP FOREIGN KEY fk_apikey_user',
    'SELECT ''api_keys.fk_apikey_user 不存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.2 billing_records.fk_billing_user
SET @fk_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'billing_records'
      AND CONSTRAINT_NAME = 'fk_billing_user' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @sql = IF(@fk_exists > 0,
    'ALTER TABLE billing_records DROP FOREIGN KEY fk_billing_user',
    'SELECT ''billing_records.fk_billing_user 不存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.3 recharge_records.fk_recharge_user
SET @fk_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recharge_records'
      AND CONSTRAINT_NAME = 'fk_recharge_user' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @sql = IF(@fk_exists > 0,
    'ALTER TABLE recharge_records DROP FOREIGN KEY fk_recharge_user',
    'SELECT ''recharge_records.fk_recharge_user 不存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.4 chat_logs.fk_chatlog_user
SET @fk_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_logs'
      AND CONSTRAINT_NAME = 'fk_chatlog_user' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @sql = IF(@fk_exists > 0,
    'ALTER TABLE chat_logs DROP FOREIGN KEY fk_chatlog_user',
    'SELECT ''chat_logs.fk_chatlog_user 不存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =============================================================
-- 2. 修改子表 user_id 列类型：BIGINT → VARCHAR(32)
--    必须在数据迁移前完成，否则 UPDATE 时将字符串赋给 BIGINT 列
--    会在 MySQL 严格模式下报 "Incorrect integer value" 错误
-- =============================================================

ALTER TABLE agents                 MODIFY COLUMN user_id VARCHAR(32) NULL COMMENT '注册用户ID(users.user_id)';
ALTER TABLE api_keys               MODIFY COLUMN user_id VARCHAR(32) NOT NULL COMMENT '归属用户ID(users.user_id)';
ALTER TABLE billing_records        MODIFY COLUMN user_id VARCHAR(32) NOT NULL COMMENT '操作用户ID(users.user_id)';
ALTER TABLE recharge_records       MODIFY COLUMN user_id VARCHAR(32) NOT NULL COMMENT '充值用户ID(users.user_id)';
ALTER TABLE chat_logs              MODIFY COLUMN user_id VARCHAR(32) NOT NULL COMMENT '用户ID(users.user_id)';
ALTER TABLE merchant_agent_binding MODIFY COLUMN user_id VARCHAR(32) NULL COMMENT '绑定的用户ID(users.user_id，雪花ID字符串)';

-- =============================================================
-- 3. 数据迁移：将子表 user_id（数字）更新为 users.user_id（字符串）
--    此时 user_id 列已是 VARCHAR(32)，可安全存储字符串雪花ID
--    通过 users.id 临时关联，将数字 ID 替换为对应的雪花ID字符串
--    使用 REGEXP 判断是否为纯数字，避免重复执行时二次更新
-- =============================================================

-- 3.1 agents 表
UPDATE agents a
JOIN users u ON a.user_id = u.id
SET a.user_id = u.user_id
WHERE a.user_id IS NOT NULL
  AND a.user_id REGEXP '^[0-9]+$';

-- 3.2 api_keys 表
UPDATE api_keys k
JOIN users u ON k.user_id = u.id
SET k.user_id = u.user_id
WHERE k.user_id REGEXP '^[0-9]+$';

-- 3.3 billing_records 表
UPDATE billing_records b
JOIN users u ON b.user_id = u.id
SET b.user_id = u.user_id
WHERE b.user_id REGEXP '^[0-9]+$';

-- 3.4 recharge_records 表
UPDATE recharge_records r
JOIN users u ON r.user_id = u.id
SET r.user_id = u.user_id
WHERE r.user_id REGEXP '^[0-9]+$';

-- 3.5 chat_logs 表
UPDATE chat_logs c
JOIN users u ON c.user_id = u.id
SET c.user_id = u.user_id
WHERE c.user_id REGEXP '^[0-9]+$';

-- 3.6 merchant_agent_binding 表（v6→v7 添加的 user_id 列，存储 users.id 数字）
UPDATE merchant_agent_binding m
JOIN users u ON m.user_id = u.id
SET m.user_id = u.user_id
WHERE m.user_id IS NOT NULL
  AND m.user_id REGEXP '^[0-9]+$';

-- 3.7 knowledge_bases 表（owner_id 存储的是 users.id 数字，需更新为 users.user_id 字符串）
--     注意：owner_type='USER' 时 owner_id 才是用户ID
UPDATE knowledge_bases kb
JOIN users u ON kb.owner_id = u.id
SET kb.owner_id = u.user_id
WHERE kb.owner_type = 'USER'
  AND kb.owner_id REGEXP '^[0-9]+$';

-- 验证：检查是否还有未迁移的数字 user_id（应返回 0 行）
SELECT '=== 数据迁移验证（未迁移的数字 user_id，应全部为 0）===' AS info;
SELECT 'agents' AS table_name, COUNT(*) AS unmigrated_cnt FROM agents WHERE user_id REGEXP '^[0-9]+$'
UNION ALL
SELECT 'api_keys', COUNT(*) FROM api_keys WHERE user_id REGEXP '^[0-9]+$'
UNION ALL
SELECT 'billing_records', COUNT(*) FROM billing_records WHERE user_id REGEXP '^[0-9]+$'
UNION ALL
SELECT 'recharge_records', COUNT(*) FROM recharge_records WHERE user_id REGEXP '^[0-9]+$'
UNION ALL
SELECT 'chat_logs', COUNT(*) FROM chat_logs WHERE user_id REGEXP '^[0-9]+$'
UNION ALL
SELECT 'merchant_agent_binding', COUNT(*) FROM merchant_agent_binding WHERE user_id IS NOT NULL AND user_id REGEXP '^[0-9]+$'
UNION ALL
SELECT 'knowledge_bases(owner_id)', COUNT(*) FROM knowledge_bases WHERE owner_type = 'USER' AND owner_id REGEXP '^[0-9]+$';

-- =============================================================
-- 4. 修改 users 表：删除 id 列，将 user_id 设为主键
--    注意：users.id 是 AUTO_INCREMENT 列，MySQL 8.0 要求 AUTO_INCREMENT
--          列必须是键的一部分，因此必须先移除 AUTO_INCREMENT 属性，
--          再删除主键，最后删除 id 列
-- =============================================================

-- 4.0 移除 id 列的 AUTO_INCREMENT 属性（如果 id 列存在且是 AUTO_INCREMENT）
SET @is_auto_increment = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'id' AND EXTRA LIKE '%auto_increment%'
);
SET @sql = IF(@is_auto_increment > 0,
    'ALTER TABLE users MODIFY COLUMN id BIGINT NOT NULL COMMENT ''主键ID（已移除自增）''',
    'SELECT ''users.id 非 AUTO_INCREMENT，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.1 删除旧主键（id 列的主键约束）
SET @has_pk_on_id = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'id' AND COLUMN_KEY = 'PRI'
);
SET @sql = IF(@has_pk_on_id > 0,
    'ALTER TABLE users DROP PRIMARY KEY',
    'SELECT ''users 表无 id 主键，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.2 删除 id 列（如果存在）
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'id'
);
SET @sql = IF(@col_exists > 0,
    'ALTER TABLE users DROP COLUMN id',
    'SELECT ''users.id 列不存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.3 删除 user_id 上的旧唯一索引 uk_user_id（如果存在），避免与主键冲突
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'
      AND INDEX_NAME = 'uk_user_id'
);
SET @sql = IF(@idx_exists > 0,
    'ALTER TABLE users DROP INDEX uk_user_id',
    'SELECT ''uk_user_id 索引不存在，跳过'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.4 修改 user_id 列定义
ALTER TABLE users MODIFY COLUMN user_id VARCHAR(32) NOT NULL COMMENT '用户唯一标识（雪花算法生成，主键）';

-- 4.5 添加主键（如果不存在）
SET @pk_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'
      AND CONSTRAINT_TYPE = 'PRIMARY KEY'
);
SET @sql = IF(@pk_exists = 0,
    'ALTER TABLE users ADD PRIMARY KEY (user_id)',
    'SELECT ''users 表已存在主键，跳过添加'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =============================================================
-- 5. 重建外键约束（引用 users.user_id）
--    先删除可能残留的同名外键，再重新创建
-- =============================================================

-- 5.1 清除 api_keys 残留外键并重建
SET @fk_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'api_keys'
      AND CONSTRAINT_NAME = 'fk_apikey_user' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @sql = IF(@fk_exists > 0,
    'ALTER TABLE api_keys DROP FOREIGN KEY fk_apikey_user',
    'SELECT ''api_keys.fk_apikey_user 不存在，跳过删除'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE api_keys
    ADD CONSTRAINT fk_apikey_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

-- 5.2 清除 billing_records 残留外键并重建
SET @fk_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'billing_records'
      AND CONSTRAINT_NAME = 'fk_billing_user' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @sql = IF(@fk_exists > 0,
    'ALTER TABLE billing_records DROP FOREIGN KEY fk_billing_user',
    'SELECT ''billing_records.fk_billing_user 不存在，跳过删除'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE billing_records
    ADD CONSTRAINT fk_billing_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

-- 5.3 清除 recharge_records 残留外键并重建
SET @fk_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recharge_records'
      AND CONSTRAINT_NAME = 'fk_recharge_user' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @sql = IF(@fk_exists > 0,
    'ALTER TABLE recharge_records DROP FOREIGN KEY fk_recharge_user',
    'SELECT ''recharge_records.fk_recharge_user 不存在，跳过删除'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE recharge_records
    ADD CONSTRAINT fk_recharge_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

-- 5.4 清除 chat_logs 残留外键并重建
SET @fk_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chat_logs'
      AND CONSTRAINT_NAME = 'fk_chatlog_user' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @sql = IF(@fk_exists > 0,
    'ALTER TABLE chat_logs DROP FOREIGN KEY fk_chatlog_user',
    'SELECT ''chat_logs.fk_chatlog_user 不存在，跳过删除'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE chat_logs
    ADD CONSTRAINT fk_chatlog_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

-- 恢复外键检查
SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
-- 6. 验证迁移结果
-- =============================================================

-- 6.1 验证 users 表主键
SELECT '=== users 表主键验证 ===' AS info;
SELECT COLUMN_NAME, COLUMN_KEY, COLUMN_TYPE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'users'
  AND COLUMN_KEY = 'PRI';

-- 6.2 验证 users 表已无 id 列
SELECT '=== users 表 id 列验证（应为空）===' AS info;
SELECT COLUMN_NAME
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'users' AND COLUMN_NAME = 'id';

-- 6.3 验证子表 user_id 列类型已改为 VARCHAR
SELECT '=== 子表 user_id 列类型验证 ===' AS info;
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'hczk_ai_platform'
  AND COLUMN_NAME = 'user_id'
  AND TABLE_NAME IN ('agents', 'api_keys', 'billing_records', 'recharge_records', 'chat_logs', 'merchant_agent_binding')
ORDER BY TABLE_NAME;

-- 6.4 验证外键约束已重建
SELECT '=== 外键约束验证 ===' AS info;
SELECT TABLE_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'hczk_ai_platform'
  AND REFERENCED_TABLE_NAME = 'users'
ORDER BY TABLE_NAME;

-- =============================================================
-- 7. 后续说明
-- =============================================================
-- 1) 此后创建用户时，由 MyBatis-Plus @TableId(type = IdType.ASSIGN_ID) 自动生成雪花ID赋值给 user_id
-- 2) 应用层所有 userId 字段统一为 String 类型
-- 3) Milvus 集合命名规则不变：kb_{user_id}_{collection_name}，user_id 直接使用雪花ID字符串
-- 4) knowledge_bases.owner_id 已同步更新为 users.user_id 字符串值
-- 5) merchant_agent_binding.user_id 已同步更新为 users.user_id 字符串值
