-- =============================================================
-- 迁移脚本：v4 → v5 — Skill 重构为"工具组 + 工具定义"结构
-- 描述：
--   1. 重构 skill 表为"工具组"（类似目录/命名空间）
--   2. 新建 tool_definition 表（具体工具定义，含执行端点）
--   3. 智能体调用时传递工具组下的所有工具及其执行端点
-- 日期：2026-06-16
-- =============================================================

-- =============================================================
-- 1. 重构 skill 表：从"技能包"改为"工具组"
-- =============================================================
-- 删除旧的技能包 JSON 字段
ALTER TABLE skill
    DROP COLUMN persona,
    DROP COLUMN capabilities,
    DROP COLUMN workflow,
    DROP COLUMN config;

-- 新增工具组字段
ALTER TABLE skill
    ADD COLUMN display_name VARCHAR(128) NULL COMMENT '工具组显示名称' AFTER name,
    ADD COLUMN icon VARCHAR(64) NULL DEFAULT 'Wrench' COMMENT '前端展示图标（Lucide icon name）' AFTER category,
    MODIFY COLUMN category VARCHAR(32) NULL DEFAULT 'custom' COMMENT '分类：knowledge / utility / custom',
    MODIFY COLUMN description TEXT COMMENT '工具组描述',
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active / inactive';

-- =============================================================
-- 2. 新建 tool_definition 表
-- =============================================================
DROP TABLE IF EXISTS tool_definition;
CREATE TABLE tool_definition (
    id                  BIGINT AUTO_INCREMENT COMMENT '主键ID'
    PRIMARY KEY,
    skill_id            VARCHAR(64)    NOT NULL COMMENT '所属工具组ID（关联 skill.id）',
    name                VARCHAR(128)   NOT NULL COMMENT '工具名称（function name，唯一标识）',
    display_name        VARCHAR(128)   NOT NULL COMMENT '工具显示名称',
    description         TEXT           NOT NULL COMMENT '工具功能描述（传给 LLM）',
    input_schema        JSON           NULL     COMMENT '输入参数 JSON Schema',
    endpoint            VARCHAR(512)   NULL     COMMENT '工具执行端点（内置工具为空则由平台内部执行；api类型填写外部URL）',
    type                VARCHAR(20)    NOT NULL DEFAULT 'builtin' COMMENT '工具类型：builtin内置(平台执行) / api外部(代理转发)',
    status              VARCHAR(20)    NOT NULL DEFAULT 'active' COMMENT '状态：active / inactive',
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    KEY idx_skill_id (skill_id),
    KEY idx_type (type),
    KEY idx_status (status),
    KEY idx_name (name),
    CONSTRAINT fk_tool_skill FOREIGN KEY (skill_id) REFERENCES skill(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具定义表';

-- =============================================================
-- 3. 创建默认的"知识库"工具组和内置工具
-- =============================================================
-- 工具组：知识库
INSERT INTO skill (id, name, display_name, category, icon, version, description, status)
VALUES ('builtin-knowledge', 'knowledge', '知识库', 'knowledge', 'Database', '1.0.0', '知识库相关操作工具组，包含检索、摄入、文件上传等工具', 'active')
ON DUPLICATE KEY UPDATE display_name='知识库', icon='Database', description='知识库相关操作工具组，包含检索、摄入、文件上传等工具';

-- 工具1：知识库检索
INSERT INTO tool_definition (skill_id, name, display_name, description, input_schema, endpoint, type, status)
VALUES ('builtin-knowledge', 'knowledge_search', '知识库检索',
        '从知识库中检索与查询相关的文档内容。使用混合检索策略（向量检索+关键词检索），返回最相关的文档分块及其相关性得分。',
        '{"type":"object","properties":{"query":{"type":"string","description":"检索查询文本，应提取用户问题中的关键信息"},"collection_name":{"type":"string","description":"知识库集合名称，如 products、faq 等"},"top_k":{"type":"integer","description":"返回结果数量，默认5","default":5}},"required":["query","collection_name"]}',
        NULL, 'builtin', 'active')
ON DUPLICATE KEY UPDATE display_name='知识库检索', description='从知识库中检索与查询相关的文档内容。使用混合检索策略（向量检索+关键词检索），返回最相关的文档分块及其相关性得分。';

-- 工具2：知识库文本摄入
INSERT INTO tool_definition (skill_id, name, display_name, description, input_schema, endpoint, type, status)
VALUES ('builtin-knowledge', 'knowledge_ingest', '知识库文本摄入',
        '将文本内容摄入到指定的知识库集合中。支持自动分块、向量化和去重。',
        '{"type":"object","properties":{"text":{"type":"string","description":"需要摄入的文本内容"},"collection_name":{"type":"string","description":"目标知识库集合名称"},"document_type":{"type":"string","description":"文档类型：auto(自动)/qa(问答对)/prose(散文)","default":"auto"},"merchant_id":{"type":"string","description":"商家ID，用于多租户隔离"}},"required":["text","collection_name"]}',
        NULL, 'builtin', 'active')
ON DUPLICATE KEY UPDATE display_name='知识库文本摄入', description='将文本内容摄入到指定的知识库集合中。支持自动分块、向量化和去重。';

-- 工具3：知识库文件上传
INSERT INTO tool_definition (skill_id, name, display_name, description, input_schema, endpoint, type, status)
VALUES ('builtin-knowledge', 'knowledge_ingest_file', '知识库文件上传',
        '上传文档文件（PDF、DOCX、TXT、XLSX 等）并摄入到知识库集合中。',
        '{"type":"object","properties":{"file_name":{"type":"string","description":"上传的文件名（含扩展名）"},"collection_name":{"type":"string","description":"目标知识库集合名称"},"document_type":{"type":"string","description":"文档类型：auto/qa/prose","default":"auto"},"merchant_id":{"type":"string","description":"商家ID，用于多租户隔离"}},"required":["file_name","collection_name"]}',
        NULL, 'builtin', 'active')
ON DUPLICATE KEY UPDATE display_name='知识库文件上传', description='上传文档文件（PDF、DOCX、TXT、XLSX 等）并摄入到知识库集合中。';

-- 工具4：列出知识库集合
INSERT INTO tool_definition (skill_id, name, display_name, description, input_schema, endpoint, type, status)
VALUES ('builtin-knowledge', 'knowledge_list_collections', '列出知识库集合',
        '列出所有可用的知识库集合及其基本信息（文档数量、行数等）。',
        '{"type":"object","properties":{"merchant_id":{"type":"string","description":"商家ID，用于过滤该商家可访问的集合"}},"required":[]}',
        NULL, 'builtin', 'active')
ON DUPLICATE KEY UPDATE display_name='列出知识库集合', description='列出所有可用的知识库集合及其基本信息（文档数量、行数等）。';

-- 工具5：查看知识分块
INSERT INTO tool_definition (skill_id, name, display_name, description, input_schema, endpoint, type, status)
VALUES ('builtin-knowledge', 'knowledge_get_chunks', '查看知识分块',
        '获取指定知识库集合中的文档分块列表，支持分页浏览。',
        '{"type":"object","properties":{"collection_name":{"type":"string","description":"知识库集合名称"},"offset":{"type":"integer","description":"分页偏移量","default":0},"limit":{"type":"integer","description":"每页数量","default":20}},"required":["collection_name"]}',
        NULL, 'builtin', 'active')
ON DUPLICATE KEY UPDATE display_name='查看知识分块', description='获取指定知识库集合中的文档分块列表，支持分页浏览。';
