-- =============================================================
-- 迁移脚本：v4 → v5 — Skill 重构（安全版，可重复执行）
-- 日期：2026-06-16
-- =============================================================

-- =============================================================
-- 0. 删除废弃的 skill 旧表
-- =============================================================
DROP TABLE IF EXISTS skill_pricing;
DROP TABLE IF EXISTS skill_rate_limit;
DROP TABLE IF EXISTS skill_usage;

-- =============================================================
-- 1. 安全删除旧列（列不存在时跳过，不中断执行）
-- =============================================================

-- 存储过程：安全删除列
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
    WHERE TABLE_SCHEMA = 'hczk_ai_platform'
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

-- 安全删除旧 JSON 列
CALL drop_column_if_exists('skill', 'persona');
CALL drop_column_if_exists('skill', 'capabilities');
CALL drop_column_if_exists('skill', 'workflow');
CALL drop_column_if_exists('skill', 'config');

DROP PROCEDURE IF EXISTS drop_column_if_exists;

-- =============================================================
-- 2. 安全添加新列
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
    WHERE TABLE_SCHEMA = 'hczk_ai_platform'
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

CALL add_column_if_not_exists('skill', 'display_name', "VARCHAR(128) NULL COMMENT '工具组显示名称'", 'name');
CALL add_column_if_not_exists('skill', 'icon', "VARCHAR(64) NULL DEFAULT 'Wrench' COMMENT '前端展示图标'", 'category');

DROP PROCEDURE IF EXISTS add_column_if_not_exists;

-- 修改已有列定义（幂等）
ALTER TABLE skill
    MODIFY COLUMN category VARCHAR(32) NULL DEFAULT 'custom' COMMENT '分类：knowledge / utility / custom',
    MODIFY COLUMN description TEXT COMMENT '工具组描述',
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active / inactive';

-- =============================================================
-- 3. 新建 tool_definition 表（skill_id 是 VARCHAR(64)，与 skill.id 一致）
-- =============================================================
DROP TABLE IF EXISTS tool_definition;

CREATE TABLE tool_definition (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    skill_id            VARCHAR(64)    NOT NULL COMMENT '所属工具组ID',
    name                VARCHAR(128)   NOT NULL COMMENT '工具名称',
    display_name        VARCHAR(128)   NOT NULL COMMENT '工具显示名称',
    description         TEXT           NOT NULL COMMENT '工具功能描述',
    input_schema        JSON           NULL     COMMENT '输入参数 JSON Schema',
    endpoint            VARCHAR(512)   NULL     COMMENT '工具执行端点',
    type                VARCHAR(20)    NOT NULL DEFAULT 'builtin' COMMENT '内置/API',
    status              VARCHAR(20)    NOT NULL DEFAULT 'active' COMMENT '状态',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_skill_id (skill_id),
    KEY idx_name (name),
    CONSTRAINT fk_tool_skill FOREIGN KEY (skill_id) REFERENCES skill(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具定义表';

-- =============================================================
-- 4. 初始化默认数据（可重复执行，不重复创建）
-- =============================================================

-- 工具组
INSERT IGNORE INTO skill (id, name, display_name, category, icon, version, description, status)
VALUES ('builtin-knowledge', 'knowledge', '知识库', 'knowledge', 'Database', '1.0.0',
        '知识库相关操作工具组，包含检索、摄入、文件上传等工具', 'active');

-- 工具定义
INSERT IGNORE INTO tool_definition (skill_id, name, display_name, description, input_schema, endpoint, type, status) VALUES
('builtin-knowledge', 'knowledge_search', '知识库检索',
 '从知识库中检索与查询相关的文档内容',
 '{"type":"object","properties":{"query":{"type":"string"},"collection_name":{"type":"string"},"top_k":{"type":"integer","default":5}},"required":["query","collection_name"]}',
 NULL, 'builtin', 'active'),

('builtin-knowledge', 'knowledge_ingest', '知识库文本摄入',
 '将文本内容摄入到知识库集合中',
 '{"type":"object","properties":{"text":{"type":"string"},"collection_name":{"type":"string"},"agent_id":{"type":"string"}},"required":["text","collection_name"]}',
 NULL, 'builtin', 'active'),

('builtin-knowledge', 'knowledge_ingest_file', '知识库文件上传',
 '上传文档文件并摄入到知识库集合中',
 '{"type":"object","properties":{"file_name":{"type":"string"},"collection_name":{"type":"string"},"agent_id":{"type":"string"}},"required":["file_name","collection_name"]}',
 NULL, 'builtin', 'active'),

('builtin-knowledge', 'knowledge_list_collections', '列出知识库集合',
 '列出所有可用的知识库集合',
 '{"type":"object","properties":{"agent_id":{"type":"string"}},"required":[]}',
 NULL, 'builtin', 'active'),

('builtin-knowledge', 'knowledge_get_chunks', '查看知识分块',
 '获取指定知识库集合中的文档分块列表',
 '{"type":"object","properties":{"collection_name":{"type":"string"},"agent_id":{"type":"string"},"offset":{"type":"integer","default":0},"limit":{"type":"integer","default":20}},"required":["collection_name"]}',
 NULL, 'builtin', 'active');
