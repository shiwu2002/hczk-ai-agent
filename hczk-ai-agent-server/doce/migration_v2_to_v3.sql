-- ============================================================
-- 数据库迁移脚本：智能体注册中心重构（幂等版本）
-- 版本：V2 -> V3
-- 日期：2026-06-16
-- 说明：将智能体从内部创建模式重构为容器注册模式，
--       每个注册的智能体提供 healthEndpoint 和 chatEndpoint
-- 特性：支持重复执行，已执行的步骤会自动跳过
-- 兼容：不使用 DELIMITER/存储过程，兼容所有 MySQL GUI 客户端
-- ============================================================

USE hczk_ai_platform;

-- ============================================================
-- 1. agents 表重构
-- ============================================================

-- 1.1 新增字段（通过 PREPARE 动态执行，列存在则跳过）
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'agents' AND COLUMN_NAME = 'health_endpoint');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE agents ADD COLUMN health_endpoint VARCHAR(256) COMMENT ''健康检测接口地址（GET 请求）'' AFTER agent_type',
    'SELECT ''health_endpoint 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'agents' AND COLUMN_NAME = 'chat_endpoint');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE agents ADD COLUMN chat_endpoint VARCHAR(256) COMMENT ''对话接口地址（POST 请求）'' AFTER health_endpoint',
    'SELECT ''chat_endpoint 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'agents' AND COLUMN_NAME = 'document_endpoint');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE agents ADD COLUMN document_endpoint VARCHAR(256) COMMENT ''文档上传接口地址（POST multipart/form-data，可选）'' AFTER chat_endpoint',
    'SELECT ''document_endpoint 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'agents' AND COLUMN_NAME = 'stream_endpoint');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE agents ADD COLUMN stream_endpoint VARCHAR(256) COMMENT ''流式对话接口地址（POST SSE，可选）'' AFTER document_endpoint',
    'SELECT ''stream_endpoint 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'agents' AND COLUMN_NAME = 'info_endpoint');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE agents ADD COLUMN info_endpoint VARCHAR(256) COMMENT ''智能体元信息接口地址（GET，可选）'' AFTER stream_endpoint',
    'SELECT ''info_endpoint 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'agents' AND COLUMN_NAME = 'history_endpoint');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE agents ADD COLUMN history_endpoint VARCHAR(256) COMMENT ''会话历史接口地址（GET/DELETE，可选）'' AFTER info_endpoint',
    'SELECT ''history_endpoint 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'agents' AND COLUMN_NAME = 'auth_header');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE agents ADD COLUMN auth_header VARCHAR(256) COMMENT ''调用接口时的认证头'' AFTER history_endpoint',
    'SELECT ''auth_header 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'agents' AND COLUMN_NAME = 'version');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE agents ADD COLUMN version VARCHAR(32) COMMENT ''智能体服务版本号'' AFTER auth_header',
    'SELECT ''version 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.2 迁移旧数据（仅当旧字段仍存在时执行）
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'agents' AND COLUMN_NAME = 'endpoint');
SET @sql = IF(@col_exists > 0,
    'UPDATE agents SET health_endpoint = REPLACE(endpoint, ''/api/chat'', ''/api/health''), chat_endpoint = endpoint, auth_header = endpoint_auth_header WHERE agent_type = ''ENDPOINT'' AND endpoint IS NOT NULL AND endpoint != ''''',
    'SELECT ''endpoint 列不存在（已是新结构），跳过数据迁移''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'agents' AND COLUMN_NAME = 'skill_id');
SET @sql = IF(@col_exists > 0,
    'UPDATE agents SET health_endpoint = ''http://127.0.0.1:3000/api/health'', chat_endpoint = ''http://127.0.0.1:3000/api/chat'' WHERE agent_type = ''SKILL'' AND skill_id IS NOT NULL AND skill_id != ''''',
    'SELECT ''skill_id 列不存在（已是新结构），跳过数据迁移''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'agents' AND COLUMN_NAME = 'model_id');
SET @sql = IF(@col_exists > 0,
    'UPDATE agents a INNER JOIN ai_models m ON a.model_id = m.id SET a.health_endpoint = CONCAT(TRIM(TRAILING ''/'' FROM m.api_base), ''/models''), a.chat_endpoint = CONCAT(TRIM(TRAILING ''/'' FROM m.api_base), ''/chat/completions'') WHERE a.agent_type = ''MODEL'' AND a.model_id IS NOT NULL AND m.api_base IS NOT NULL',
    'SELECT ''model_id 列不存在（已是新结构），跳过数据迁移''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.3 将 agent_type 从固定枚举改为自由文本（UPDATE 本身幂等）
UPDATE agents SET agent_type = 'model_agent'    WHERE agent_type = 'MODEL';
UPDATE agents SET agent_type = 'skill_agent'    WHERE agent_type = 'SKILL';
UPDATE agents SET agent_type = 'endpoint_agent' WHERE agent_type = 'ENDPOINT';

-- ============================================================
-- 1.5 先删除旧外键和索引（必须在删列之前！）
-- ============================================================
SET @fk_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_TYPE = 'FOREIGN KEY' AND TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'agents' AND CONSTRAINT_NAME = 'fk_agent_model');
SET @sql = IF(@fk_exists > 0, 'ALTER TABLE agents DROP FOREIGN KEY fk_agent_model', 'SELECT ''fk_agent_model 不存在，跳过删除''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_TYPE = 'FOREIGN KEY' AND TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'agents' AND CONSTRAINT_NAME = 'fk_agent_user');
SET @sql = IF(@fk_exists > 0, 'ALTER TABLE agents DROP FOREIGN KEY fk_agent_user', 'SELECT ''fk_agent_user 不存在，跳过删除''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'agents' AND INDEX_NAME = 'idx_model_id');
SET @sql = IF(@idx_exists > 0, 'ALTER TABLE agents DROP INDEX idx_model_id', 'SELECT ''idx_model_id 不存在，跳过删除''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.4 再删除旧字段（外键已清除，可以安全删列）
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'agents' AND COLUMN_NAME = 'model_id');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE agents DROP COLUMN model_id', 'SELECT ''model_id 不存在，跳过删除''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'agents' AND COLUMN_NAME = 'skill_id');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE agents DROP COLUMN skill_id', 'SELECT ''skill_id 不存在，跳过删除''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'agents' AND COLUMN_NAME = 'endpoint');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE agents DROP COLUMN endpoint', 'SELECT ''endpoint 不存在，跳过删除''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'agents' AND COLUMN_NAME = 'endpoint_auth_header');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE agents DROP COLUMN endpoint_auth_header', 'SELECT ''endpoint_auth_header 不存在，跳过删除''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.6 user_id 改为可空
ALTER TABLE agents MODIFY COLUMN user_id BIGINT COMMENT '注册用户ID(users.id)';

-- 1.7 health_endpoint 和 chat_endpoint 设为 NOT NULL
ALTER TABLE agents
    MODIFY COLUMN health_endpoint VARCHAR(256) NOT NULL COMMENT '健康检测接口地址（GET 请求）',
    MODIFY COLUMN chat_endpoint   VARCHAR(256) NOT NULL COMMENT '对话接口地址（POST 请求）';

-- 1.8 更新表注释
ALTER TABLE agents COMMENT='容器智能体注册表';

-- ============================================================
-- 2. merchant_agent_binding 表新增 agent_id 字段
-- ============================================================

-- 2.1 新增 agent_id 列
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'merchant_agent_binding' AND COLUMN_NAME = 'agent_id');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE merchant_agent_binding ADD COLUMN agent_id BIGINT COMMENT ''绑定的平台注册智能体ID（优先级最高）'' AFTER merchant_id',
    'SELECT ''agent_id 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.2 新增索引
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'hczk_ai_platform' AND TABLE_NAME = 'merchant_agent_binding' AND INDEX_NAME = 'idx_agent_id');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE merchant_agent_binding ADD KEY idx_agent_id (agent_id)',
    'SELECT ''idx_agent_id 已存在，跳过''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.3 更新表注释
ALTER TABLE merchant_agent_binding COMMENT='商家智能体绑定表（支持平台智能体、Skill、Endpoint 三种模式）';

-- ============================================================
-- 迁移完成验证
-- ============================================================

SELECT '=== 验证 agents 表结构 ===' AS info;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'hczk_ai_platform'
  AND TABLE_NAME = 'agents'
ORDER BY ORDINAL_POSITION;

SELECT '=== 验证 merchant_agent_binding 表结构 ===' AS info;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'hczk_ai_platform'
  AND TABLE_NAME = 'merchant_agent_binding'
ORDER BY ORDINAL_POSITION;

SELECT '=== 验证无遗漏的空 endpoint ===' AS info;
SELECT id, name, health_endpoint, chat_endpoint
FROM agents
WHERE health_endpoint IS NULL OR health_endpoint = ''
   OR chat_endpoint IS NULL OR chat_endpoint = '';
