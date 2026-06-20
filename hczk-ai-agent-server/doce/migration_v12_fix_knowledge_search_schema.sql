-- ============================================================
-- v12: 修复 knowledge_search 工具的 input_schema
-- 1. collection_name 改为可选（默认 default），避免外部智能体因不知道集合名而传错
-- 2. user_id 为必填参数，必须由智能体在调用时携带
-- ============================================================

UPDATE tool_definition
SET input_schema = '{"type":"object","properties":{"query":{"type":"string","description":"检索查询文本"},"collection_name":{"type":"string","default":"default","description":"知识库集合名，默认为default"},"user_id":{"type":"string","description":"用户ID（雪花ID），智能体必须携带此参数用于定位用户的知识库集合"},"top_k":{"type":"integer","default":5,"description":"返回结果数量"}},"required":["query","user_id"]}'
WHERE name = 'knowledge_search';
