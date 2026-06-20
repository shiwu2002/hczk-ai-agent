-- ============================================================
-- v11: 知识库检索日志表 + 入库操作日志表 + 服务健康指标表
-- ============================================================

-- 1. 检索日志表（含得分分布、块类型分布、间距、采纳反馈）
CREATE TABLE IF NOT EXISTS `retrieval_logs` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_id`          VARCHAR(64)  NOT NULL                COMMENT '智能体ID（用户ID）',
  `collection_name`   VARCHAR(128) NOT NULL DEFAULT 'default' COMMENT '集合名',
  `query`             TEXT         NOT NULL                COMMENT '检索查询文本',
  `strategy`          VARCHAR(16)           DEFAULT 'fallback' COMMENT '检索策略 hybrid/vector/keyword/fallback',
  `hit_success`       TINYINT(1)            DEFAULT 0     COMMENT '是否命中有效结果',
  `hit_count`         INT                   DEFAULT 0     COMMENT '命中结果数量',
  `top_k`             INT                   DEFAULT 5     COMMENT '请求的topK',
  `top_score`         DOUBLE                DEFAULT 0     COMMENT '最高得分',
  `min_score`         DOUBLE                DEFAULT 0     COMMENT '最低得分',
  `avg_score`         DOUBLE                DEFAULT 0     COMMENT '平均得分',
  `confidence`        DOUBLE                DEFAULT 0     COMMENT '置信度',
  `fallback_used`     TINYINT(1)            DEFAULT 0     COMMENT '是否降级到兜底回复',
  `hit_sources`       TEXT                               COMMENT '命中块溯源摘要JSON（页码/章节/源文件/得分/类型）',
  `chunk_type_dist`   VARCHAR(512)          DEFAULT '{}'  COMMENT '命中块类型分布JSON，如 {"prose":3,"qa":1,"table":1}',
  `avg_distance`      DOUBLE                DEFAULT 0     COMMENT '查询与命中块的平均向量距离',
  `adopted`           TINYINT(1)            DEFAULT NULL  COMMENT '用户是否采纳（NULL=未反馈/1=采纳/0=拒绝）',
  `duration_ms`       BIGINT                DEFAULT 0     COMMENT '检索耗时（毫秒）',
  `created_at`        DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_agent_id` (`agent_id`),
  INDEX `idx_created_at` (`created_at`),
  INDEX `idx_strategy` (`strategy`),
  INDEX `idx_hit_success` (`hit_success`),
  INDEX `idx_adopted` (`adopted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库检索日志';

-- 2. 入库操作日志表
CREATE TABLE IF NOT EXISTS `ingest_logs` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_id`          VARCHAR(64)  NOT NULL                COMMENT '智能体ID',
  `collection_name`   VARCHAR(128) NOT NULL DEFAULT 'default' COMMENT '集合名',
  `source`            VARCHAR(512)                          COMMENT '来源标识（文件名等）',
  `doc_type`          VARCHAR(16)           DEFAULT 'auto' COMMENT '文档类型 auto/qa/prose',
  `file_type`         VARCHAR(16)                          COMMENT '文件类型 pdf/docx/txt/xlsx',
  `total_pages`       INT                   DEFAULT 0     COMMENT '文档总页数',
  `total_chunks`      INT                   DEFAULT 0     COMMENT '入库分块数',
  `ocr_used`          TINYINT(1)            DEFAULT 0     COMMENT '是否使用了OCR',
  `llm_preprocess_used` TINYINT(1)          DEFAULT 0     COMMENT '是否使用了LLM预处理',
  `llm_preprocess_ms` BIGINT                DEFAULT 0     COMMENT 'LLM预处理耗时（毫秒）',
  `ingest_duration_ms` BIGINT               DEFAULT 0     COMMENT '入库总耗时（毫秒）',
  `status`            VARCHAR(16)           DEFAULT 'success' COMMENT '状态 success/error',
  `error_message`     TEXT                               COMMENT '错误信息',
  `created_at`        DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_agent_id` (`agent_id`),
  INDEX `idx_created_at` (`created_at`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库入库操作日志';

-- 3. 服务健康指标表
CREATE TABLE IF NOT EXISTS `service_health_metrics` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `service_name`      VARCHAR(64)  NOT NULL                COMMENT '服务名（embedder/milvus/ocr/llm_preprocess）',
  `operation`         VARCHAR(64)  NOT NULL                COMMENT '操作名（embed/embed_batch/search/insert/ocr_image/preprocess）',
  `success`           TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '是否成功',
  `duration_ms`       BIGINT                DEFAULT 0     COMMENT '耗时（毫秒）',
  `error_type`        VARCHAR(128)                        COMMENT '错误类型',
  `detail`            VARCHAR(512)                        COMMENT '详情（模型名、集合名等）',
  `created_at`        DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_service_name` (`service_name`),
  INDEX `idx_created_at` (`created_at`),
  INDEX `idx_success` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务健康指标';


SHOW TABLES LIKE 'retrieval_logs';
SHOW TABLES LIKE 'ingest_logs';
SHOW TABLES LIKE 'service_health_metrics';