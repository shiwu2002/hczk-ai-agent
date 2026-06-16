-- =============================================================
-- 迁移脚本：v7 → v8 — agentId 改用雪花ID（纯数字）
-- 日期：2026-06-17
-- 说明：知识库集合名从 kb_user_{userId}_{collection} 改为 kb_{userId}_{collection}
--       agent_id 字段从 "user_5" / "agent_1" 改为纯数字 "5" / "1"
--
-- 注意：Milvus 不支持 rename collection，旧集合 kb_user_* / kb_agent_* 保留不动。
--       新上传/检索将使用新格式集合名 kb_{snowflakeId}_{collection}。
--       如需迁移历史向量数据，需手动创建新集合并重新导入。
-- =============================================================

-- =============================================================
-- 1. 更新 knowledge_bases 表：agent_id 去除 user_/agent_ 前缀
-- =============================================================

-- 去除 "user_" 前缀
UPDATE knowledge_bases
SET agent_id = SUBSTRING(agent_id, 6),
    updated_at = NOW()
WHERE agent_id LIKE 'user\_%' ESCAPE '\\';

-- 去除 "agent_" 前缀
UPDATE knowledge_bases
SET agent_id = SUBSTRING(agent_id, 7),
    updated_at = NOW()
WHERE agent_id LIKE 'agent\_%' ESCAPE '\\';

-- 验证：检查是否还有非纯数字的 agent_id
SELECT id, name, owner_type, owner_id, agent_id, collection_name
FROM knowledge_bases
WHERE agent_id NOT REGEXP '^[0-9]+$';

-- 提示：如有上述查询有结果，需手动检查处理

-- =============================================================
-- 2. Milvus 集合迁移说明（手动执行）
-- =============================================================
-- Milvus 不支持 rename collection，旧集合保留：
--   kb_user_5_default       → 保留（历史数据）
--   kb_agent_1_default      → 保留（历史数据）
--
-- 新格式集合名（自动创建）：
--   kb_5_default            → 新上传时懒创建
--   kb_1_default            → 新上传时懒创建
--
-- 如需迁移历史数据，可选方案：
--   1. 导出旧集合数据 → 通过 /knowledge/ingest 接口重新导入到新集合
--   2. 或保留旧集合，在应用层做兼容查询（不推荐，增加复杂度）
--   3. 或直接删除旧集合重新上传（适合数据量小的场景）
