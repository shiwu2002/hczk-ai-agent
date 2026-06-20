-- ============================================================
-- v11: 知识库检索日志表
-- 记录每次 RAG 检索的查询、命中情况、策略和溯源信息
-- 用于管理员监控检索命中率变化和检索质量
-- ============================================================

CREATE TABLE IF NOT EXISTS `retrieval_logs` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_id`        VARCHAR(64)  NOT NULL                COMMENT '智能体ID（用户ID）',
  `collection_name` VARCHAR(128) NOT NULL DEFAULT 'default' COMMENT '集合名',
  `query`           TEXT         NOT NULL                COMMENT '检索查询文本',
  `strategy`        VARCHAR(16)           DEFAULT 'fallback' COMMENT '检索策略 hybrid/vector/keyword/fallback',
  `hit_success`     TINYINT(1)            DEFAULT 0     COMMENT '是否命中有效结果',
  `hit_count`       INT                   DEFAULT 0     COMMENT '命中结果数量',
  `top_k`           INT                   DEFAULT 5     COMMENT '请求的topK',
  `top_score`       DOUBLE                DEFAULT 0     COMMENT '最高得分',
  `confidence`      DOUBLE                DEFAULT 0     COMMENT '置信度',
  `fallback_used`   TINYINT(1)            DEFAULT 0     COMMENT '是否降级到兜底回复',
  `hit_sources`     TEXT                               COMMENT '命中块溯源摘要JSON（页码/章节/源文件）',
  `duration_ms`     BIGINT                DEFAULT 0     COMMENT '检索耗时（毫秒）',
  `created_at`      DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_agent_id` (`agent_id`),
  INDEX `idx_created_at` (`created_at`),
  INDEX `idx_strategy` (`strategy`),
  INDEX `idx_hit_success` (`hit_success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库检索日志';
