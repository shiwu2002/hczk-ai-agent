-- =============================================================
-- 迁移脚本：v8 → v9 — users 表主键切换为 user_id（雪花ID）
-- 日期：2026-06-17
-- 说明：删除 users.id（BIGINT 自增主键），将 user_id（VARCHAR(32) 雪花ID）设为主键
--       所有外键表（agents / api_keys / billing_records / recharge_records / chat_logs）
--       的 user_id 列类型从 BIGINT 改为 VARCHAR(32)，并更新数据为对应的 user_id 字符串值
--
-- 前置条件：
--   1. users 表已存在 user_id VARCHAR(32) NOT NULL UNIQUE 字段
--   2. 所有子表的 user_id 当前存储的是 users.id（BIGINT 数字）
--
-- 注意：执行前请务必备份数据库！
-- =============================================================

USE hczk_ai_platform;

-- =============================================================
-- 1. 数据迁移：将子表 user_id（数字）更新为 users.user_id（字符串）
--    通过 users.id 临时关联，将数字 ID 替换为对应的雪花ID字符串
-- =============================================================

-- 1.1 agents 表
UPDATE agents a
JOIN users u ON a.user_id = u.id
SET a.user_id = u.user_id
WHERE a.user_id IS NOT NULL;

-- 1.2 api_keys 表
UPDATE api_keys k
JOIN users u ON k.user_id = u.id
SET k.user_id = u.user_id;

-- 1.3 billing_records 表
UPDATE billing_records b
JOIN users u ON b.user_id = u.id
SET b.user_id = u.user_id;

-- 1.4 recharge_records 表
UPDATE recharge_records r
JOIN users u ON r.user_id = u.id
SET r.user_id = u.user_id;

-- 1.5 chat_logs 表
UPDATE chat_logs c
JOIN users u ON c.user_id = u.id
SET c.user_id = u.user_id;

-- 验证：检查是否还有未迁移的数字 user_id（应返回 0 行）
SELECT 'agents 未迁移' AS check_name, COUNT(*) AS cnt FROM agents WHERE user_id REGEXP '^[0-9]+$'
UNION ALL
SELECT 'api_keys 未迁移', COUNT(*) FROM api_keys WHERE user_id REGEXP '^[0-9]+$'
UNION ALL
SELECT 'billing_records 未迁移', COUNT(*) FROM billing_records WHERE user_id REGEXP '^[0-9]+$'
UNION ALL
SELECT 'recharge_records 未迁移', COUNT(*) FROM recharge_records WHERE user_id REGEXP '^[0-9]+$'
UNION ALL
SELECT 'chat_logs 未迁移', COUNT(*) FROM chat_logs WHERE user_id REGEXP '^[0-9]+$';

-- =============================================================
-- 2. 删除外键约束（引用 users.id）
-- =============================================================

ALTER TABLE api_keys         DROP FOREIGN KEY IF EXISTS fk_apikey_user;
ALTER TABLE billing_records  DROP FOREIGN KEY IF EXISTS fk_billing_user;
ALTER TABLE recharge_records DROP FOREIGN KEY IF EXISTS fk_recharge_user;
ALTER TABLE chat_logs        DROP FOREIGN KEY IF EXISTS fk_chatlog_user;

-- =============================================================
-- 3. 修改子表 user_id 列类型：BIGINT → VARCHAR(32)
-- =============================================================

ALTER TABLE agents           MODIFY COLUMN user_id VARCHAR(32) COMMENT '注册用户ID(users.user_id)';
ALTER TABLE api_keys         MODIFY COLUMN user_id VARCHAR(32) NOT NULL COMMENT '归属用户ID(users.user_id)';
ALTER TABLE billing_records  MODIFY COLUMN user_id VARCHAR(32) NOT NULL COMMENT '操作用户ID(users.user_id)';
ALTER TABLE recharge_records MODIFY COLUMN user_id VARCHAR(32) NOT NULL COMMENT '充值用户ID(users.user_id)';
ALTER TABLE chat_logs        MODIFY COLUMN user_id VARCHAR(32) NOT NULL COMMENT '用户ID(users.user_id)';

-- =============================================================
-- 4. 修改 users 表：删除 id 列，将 user_id 设为主键
-- =============================================================

-- 4.1 删除旧主键（id 列的主键约束）
ALTER TABLE users DROP PRIMARY KEY;

-- 4.2 删除 id 列
ALTER TABLE users DROP COLUMN id;

-- 4.3 将 user_id 设为主键
ALTER TABLE users MODIFY COLUMN user_id VARCHAR(32) NOT NULL COMMENT '用户唯一标识（雪花算法生成，主键）';
ALTER TABLE users ADD PRIMARY KEY (user_id);

-- 4.4 删除 user_id 上的旧唯一索引（已升级为主键，避免冗余）
-- 注意：MySQL 中主键自动具有唯一性，原 uk_user_id 索引可删除
ALTER TABLE users DROP INDEX IF EXISTS uk_user_id;

-- =============================================================
-- 5. 重建外键约束（引用 users.user_id）
-- =============================================================

ALTER TABLE api_keys
    ADD CONSTRAINT fk_apikey_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

ALTER TABLE billing_records
    ADD CONSTRAINT fk_billing_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

ALTER TABLE recharge_records
    ADD CONSTRAINT fk_recharge_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

ALTER TABLE chat_logs
    ADD CONSTRAINT fk_chatlog_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

-- =============================================================
-- 6. 验证迁移结果
-- =============================================================

-- 验证 users 表主键
SHOW KEYS FROM users WHERE Key_name = 'PRIMARY';

-- 验证 users 表已无 id 列
SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'users' AND COLUMN_NAME = 'id';

-- 验证子表 user_id 列类型已改为 VARCHAR
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'hczk_ai_platform'
  AND COLUMN_NAME = 'user_id'
  AND TABLE_NAME IN ('agents', 'api_keys', 'billing_records', 'recharge_records', 'chat_logs');

-- =============================================================
-- 7. 后续说明
-- =============================================================
-- 1) 此后创建用户时，由 MyBatis-Plus @TableId(type = IdType.ASSIGN_ID) 自动生成雪花ID赋值给 user_id
-- 2) 应用层所有 userId 字段统一为 String 类型
-- 3) Milvus 集合命名规则不变：kb_{user_id}_{collection_name}，user_id 直接使用雪花ID字符串
