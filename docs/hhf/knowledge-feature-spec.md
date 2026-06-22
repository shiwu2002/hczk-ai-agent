# 知识库服务功能全景文档

> 版本：7.0.0（Java 版）
> 日期：2026-06-22
> 说明：本文档描述 Java 版知识库服务的完整功能规格，包含 TS 版参考和 Java 实现说明。

---

## 一、项目概述

**项目名称**：hczk-knowledge-service
**技术栈**：Java + Spring Boot + Milvus Java SDK + OpenAI Embedding API
**定位**：独立的 Milvus 知识库服务，提供向量检索和知识管理 API，为 AI 智能体提供知识检索支撑。

---

## 二、整体架构

```
┌─────────────────────────────────────────────────────┐
│              Spring Boot HTTP Server                  │
│  ┌──────────┐  ┌──────────┐  ┌───────────────────┐  │
│  │ Auth     │  │ Validation│  │ Rate Limiter      │  │
│  │ Filter   │  │          │  │ (ingest 30/min)   │  │
│  └──────────┘  └──────────┘  └───────────────────┘  │
│  ┌─────────────────────────────────────────────────┐  │
│  │           Knowledge Controller (/knowledge)      │  │
│  │  ┌─────────┐ ┌──────────┐ ┌──────────────────┐ │  │
│  │  │Ingester │ │Retriever │ │ RelevanceScorer  │ │  │
│  │  └────┬────┘ └────┬─────┘ └────────┬─────────┘ │  │
│  │       │           │                │            │  │
│  │  ┌────┴────┐      │           ┌────┴─────┐      │  │
│  │  │Embedder │      │           │MilvusMgr │      │  │
│  │  └────┬────┘      │           └────┬─────┘      │  │
│  │       │           │                │            │  │
│  │  ┌────┴────┐      │           ┌────┴─────┐      │  │
│  │  │LLMClient│      │           │  Milvus  │      │  │
│  │  │(Embed)  │      │           │ (VectorDB)│      │  │
│  │  └─────────┘      │           └──────────┘      │  │
│  └─────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

---

## 三、API 接口清单

> 详细接口文档见 `knowledge-api.md`

### 3.1 健康检查

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/knowledge/health` | 健康检查 |

### 3.2 知识检索

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/knowledge/retrieve` | 检索知识（多策略降级） |

**请求参数**：
```json
{
  "agentId": "agent_001",
  "query": "如何退款",
  "collectionName": "default",
  "topK": 5
}
```

**响应示例**：
```json
{
  "code": 200,
  "data": {
    "results": [
      {
        "content": "退款需联系客服...",
        "score": 0.85,
        "metadata": {
          "source": "faq",
          "title": "退款政策",
          "chunkType": "qa",
          "chunkId": "abc123",
          "relevanceScore": 1.5
        }
      }
    ],
    "strategy": "hybrid",
    "fallbackUsed": false
  }
}
```

### 3.3 知识摄入

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/knowledge/ingest/file` | 文件上传摄入 |
| POST | `/knowledge/ingest/text` | 纯文本摄入 |
| POST | `/knowledge/ingest/json` | JSON 结构化摄入 |

### 3.4 集合管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/knowledge/collections` | 列出集合 |
| DELETE | `/knowledge/collections/{name}` | 删除集合 |
| GET | `/knowledge/collections/{name}/chunks` | 浏览块 |

### 3.5 反馈与评分

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/knowledge/chunks/{id}/adopt` | 标记采纳 |
| POST | `/knowledge/recalculate` | 重算相关性评分 |

### 3.6 知识库归属管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/knowledge/agents/{agentId}/collections` | 获取智能体的知识库集合 |

---

## 四、核心功能模块详解

### 4.1 知识摄入 (Ingester)

**处理流程**：
1. 接收文本 → 确保集合存在（自动创建）
2. 智能分块（Chunker）
3. 内容去重（SHA-256 hash）
4. 批量向量化 content（Embedder）
5. 对 QA chunk 的 question 单独向量化
6. 分批插入 Milvus
7. Flush 确保持久化

**智能分块 (Chunker)**：
- **Q&A 格式检测**：自动识别 `Q:/问:/问题:` + `A:/答:/答案:` 格式
- **QA 分块**：每个问答对作为独立 chunk，`question` 字段单独向量化，`chunkType = "qa"`
- **普通文本分块**：按语句切分（中英文标点感知），块间重叠 2 句，`chunkType = "prose"`
- 默认块大小：800 字符

### 4.2 知识检索 (Retriever) — 多策略降级

采用**四级降级策略**：

| 优先级 | 策略 | 说明 | 置信度阈值 |
|--------|------|------|-----------|
| 1 | **混合检索 (Hybrid)** | 同时搜索 content 向量 + question 向量，question 匹配加权 1.2x | 0.7 |
| 2 | **纯向量检索 (Vector)** | 扩大 topK 3 倍搜索 content 向量 | 0.5 |
| 3 | **关键词检索 (Keyword)** | 提取查询关键词，用 Milvus `like` 过滤匹配 | 0.3 |
| 4 | **兜底回复 (Fallback)** | 预设 FAQ 关键词匹配，无匹配返回默认兜底话术 | 0.2 |

**relevance_score 调权**：检索结果分数 = 向量相似度 × normalizeRelevance(relevance_score)，归一化范围 [0.2, 2.0]。

### 4.3 相关性评分 (RelevanceScorer)

**评分公式**：
```
relevance_score = ageFactor × (1 + adoptRatio × 2.0 + adoptBonus × 0.1)
```

| 因子 | 计算方式 | 说明 |
|------|---------|------|
| ageFactor | 0.5^(ageMs / HALF_LIFE_MS) | 时间衰减因子，半衰期 30 天 |
| adoptRatio | adopt_count / max(hit_count, 1) | 采纳率 |
| adoptBonus | log(1 + adopt_count) | 绝对采纳数奖励 |

- 评分范围：[0.01, 10.0]，默认 1.0
- 仅在分数变化超过 0.01 时写入更新

### 4.4 Embedding 服务

- 通过 OpenAI 兼容 API 调用 Embedding 模型
- **LRU 缓存**：最多 10000 条，避免重复向量化
- **批量向量化**：自动分离缓存命中/未命中
- **熔断保护**：5 次连续失败后熔断，30 秒后半开探测
- **重试机制**：指数退避重试，最多 2 次

### 4.5 Milvus 管理

- **自动连接/重连**：启动时连接，断开后每 5 秒尝试重连
- **集合自动创建**：首次写入时自动创建集合、索引并加载到内存
- **集合命名**：`kb_{agentId}_{collectionName}`，通过前缀隔离不同 Agent

---

## 五、数据模型

### 5.1 Milvus Collection Schema

Collection 命名规则：`kb_{agentId}_{collectionName}`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VarChar(64) | 主键 | hash 生成 |
| content | VarChar(65535) | | 文本内容 |
| vector | FloatVector(dim) | | 内容向量 |
| question | VarChar(4096) | | 问题文本（QA chunk） |
| question_vector | FloatVector(dim) | | 问题向量（QA chunk） |
| source | VarChar(512) | | 来源标识 |
| title | VarChar(256) | | 文档标题 |
| chunk_index | Int64 | | 块序号 |
| content_hash | VarChar(64) | | 内容哈希（去重） |
| created_at | Int64 | | 创建时间戳 |
| ingest_time | Int64 | | 入库时间戳 |
| hit_count | Int64 | | 检索命中次数 |
| adopt_count | Int64 | | 被采纳次数 |
| relevance_score | Float | | 动态相关性评分 |
| chunk_type | VarChar(16) | | 块类型：`prose` / `qa` |

### 5.2 索引

| 字段 | 索引类型 | 度量方式 | 参数 |
|------|---------|---------|------|
| vector | IVF_FLAT | COSINE | nlist=1024 |
| question_vector | IVF_FLAT | COSINE | nlist=1024 |

---

## 六、配置项

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| MILVUS_ADDRESS | localhost:19530 | Milvus 连接地址 |
| EMBEDDING_BASE_URL | http://127.0.0.1:1234/v1 | Embedding API 地址 |
| EMBEDDING_API_KEY | 空 | Embedding API 密钥 |
| EMBEDDING_MODEL | text-embedding-qwen3-embedding | 向量化模型名 |
| EMBEDDING_DIMENSION | 2560 | 向量维度 |
| SERVER_PORT | 3001 | 服务端口 |
| API_KEY | 空（不启用） | API 认证密钥 |

---

## 七、Java 重写注意事项（TS → Java）

1. **Milvus Java SDK**：使用 `milvus-sdk-java`，API 与 Node SDK 有差异
2. **Embedding 调用**：Java 可用 OkHttp/WebClient 或 OpenAI Java SDK
3. **向量索引**：IVF_FLAT + COSINE，Java 端需确认 SDK 对应参数名
4. **Upsert 语义**：hit_count/adopt_count 更新采用 query 全字段 + upsert 方式
5. **异步处理**：Java 可用 @Async 或 CompletableFuture 替代 fire-and-forget
6. **熔断/限流**：Java 可用 Resilience4j 替代自实现
7. **分块逻辑**：中英文语句分割和 QA 格式检测需在 Java 中重新实现
8. **Collection 命名**：`kb_{agentId}_{collectionName}` 规则需保持一致
