# Milvus Knowledge Service 功能全景文档

> 本文档详细描述了 `milvus-knowledge-service` 应用的全部功能，作为 Java 重写的参考依据。

## 一、项目概述

**项目名称**：milvus-knowledge-service
**技术栈**：TypeScript + Express + Milvus SDK + OpenAI Embedding API
**定位**：独立的 Milvus 知识库服务，提供向量检索和知识管理 API，为 AI Agent 提供知识检索支撑。

## 二、整体架构

```
┌─────────────────────────────────────────────────────┐
│                  Express HTTP Server                 │
│  ┌──────────┐  ┌──────────┐  ┌───────────────────┐  │
│  │ Auth     │  │ Validation│  │ Rate Limiter      │  │
│  │ Middleware│  │ Middleware│  │ (ingest 30/min)   │  │
│  └──────────┘  └──────────┘  └───────────────────┘  │
│  ┌─────────────────────────────────────────────────┐  │
│  │           Knowledge Router (/api/knowledge)      │  │
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

## 三、API 接口清单

### 3.1 健康检查

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health/live` | 存活检查，进程存活即返回 200 |
| GET | `/health/ready` | 就绪检查，验证 Milvus + Embedding 服务是否可用 |
| GET | `/health` | 旧版健康检查，仅检查 Milvus 连接 |

**就绪检查返回示例**：
```json
{
  "status": "ready",
  "milvus": "connected",
  "embedding": "available"
}
```

### 3.2 知识检索

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/knowledge/retrieve` | 检索知识（多策略降级） |

**请求参数**：
```json
{
  "agent_id": "agent_001",       // 必填，智能体ID
  "query": "如何退款",           // 必填，查询文本
  "collection": "default",       // 可选，集合名，默认 default
  "topK": 5                      // 可选，返回条数，默认5，最大50
}
```

**响应示例**：
```json
{
  "results": [
    {
      "content": "退款需联系客服...",
      "score": 0.85,
      "metadata": {
        "source": "faq",
        "title": "退款政策",
        "chunkIndex": 0,
        "question": "如何退款",
        "matchType": "question",
        "chunkId": "abc123",
        "hitCount": 10,
        "adoptCount": 3,
        "relevanceScore": 1.5,
        "chunkType": "qa"
      }
    }
  ],
  "strategy": "hybrid",
  "confidence": 0.85,
  "fallbackUsed": false
}
```

### 3.3 知识摄入

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/knowledge/ingest` | 摄入纯文本文档 |
| POST | `/api/knowledge/ingest/json` | 摄入 JSON 数组数据 |

**文本摄入请求**：
```json
{
  "agent_id": "agent_001",       // 必填
  "text": "文档内容...",          // 必填，最大5MB
  "collection": "default",       // 可选
  "source": "api",               // 可选，来源标识
  "title": "文档标题"            // 可选
}
```

**JSON 批量摄入请求**：
```json
{
  "agent_id": "agent_001",
  "collection": "default",
  "data": [
    { "content": "内容1", "title": "标题1", "source": "source1" },
    { "content": "内容2", "title": "标题2", "source": "source2" }
  ]
}
```

**摄入响应**：
```json
{
  "totalChunks": 10,
  "ingestedChunks": 10,
  "skippedChunks": 0
}
```

### 3.4 知识管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/knowledge/collections` | 列出所有集合，支持 `?agent_id=xxx` 筛选 |
| DELETE | `/api/knowledge/collections/:name` | 删除集合（需 agent_id） |
| GET | `/api/knowledge/collections/:name/chunks` | 浏览集合中的块 |
| POST | `/api/knowledge/chunks/:id/adopt` | 标记块被采纳 |
| POST | `/api/knowledge/recalculate` | 重算相关性评分 |

**浏览块请求参数**：
- `agent_id`（query，必填）
- `chunkType`（query，可选）：按块类型筛选 `prose` / `qa`
- `source`（query，可选）：按来源筛选
- `limit`（query，可选）：返回条数，默认100，最大1000

**采纳请求**：
```json
{
  "agent_id": "agent_001",
  "collection": "default"
}
```

**重算评分请求**：
```json
{
  "agent_id": "agent_001",       // 必填
  "collection": "default"        // 可选，不填则重算该 agent 所有集合
}
```

## 四、核心功能模块详解

### 4.1 知识摄入 (Ingester)

**处理流程**：
1. 接收文本 → 确保集合存在（自动创建）
2. 智能分块（Chunker）
3. 内容去重（SHA-256 hash）
4. 批量向量化 content（Embedder，每批20条）
5. 对 QA chunk 的 question 单独向量化
6. 分批插入 Milvus（每批100条）
7. Flush 确保持久化

**智能分块 (Chunker)**：
- **Q&A 格式检测**：自动识别 `Q:/问:/问题:` + `A:/答:/答案:` 格式，至少2个问答对才判定为 QA 格式
- **QA 分块**：每个问答对作为独立 chunk，`question` 字段单独向量化，`chunk_type = "qa"`
- **普通文本分块**：按语句切分（中英文标点感知：。！？.!?换行），块间重叠2句保证上下文连贯，`chunk_type = "prose"`
- 默认块大小：800 字符

### 4.2 知识检索 (Retriever) — 多策略降级

采用**四级降级策略**，优先使用高置信度结果：

| 优先级 | 策略 | 说明 | 置信度阈值 |
|--------|------|------|-----------|
| 1 | **混合检索 (Hybrid)** | 同时搜索 content 向量 + question 向量，question 匹配加权 1.2x，结果去重取高分，用 relevance_score 调权 | 0.7 |
| 2 | **纯向量检索 (Vector)** | 扩大 topK 3 倍搜索 content 向量 | 0.5 |
| 3 | **关键词检索 (Keyword)** | 提取查询关键词（中英文，最多5个），用 Milvus `like` 过滤匹配 | 0.3 |
| 4 | **兜底回复 (Fallback)** | 预设 FAQ 关键词匹配（人工/退款/投诉），无匹配返回默认兜底话术 | 0.2 |

**relevance_score 调权**：检索结果分数 = 向量相似度 × normalizeRelevance(relevance_score)，归一化范围 [0.2, 2.0]。

**异步记录命中**：检索成功后异步更新 `hit_count`，不影响响应速度。

### 4.3 相关性评分 (RelevanceScorer)

**评分公式**：
```
relevance_score = ageFactor × (1 + adoptRatio × 2.0 + adoptBonus × 0.1)
```

| 因子 | 计算方式 | 说明 |
|------|---------|------|
| ageFactor | 0.5^(ageMs / HALF_LIFE_MS) | 时间衰减因子，半衰期30天 |
| adoptRatio | adopt_count / max(hit_count, 1) | 采纳率，高采纳率说明知识有效 |
| adoptBonus | log(1 + adopt_count) | 绝对采纳数奖励，多次采纳更可靠 |

- 评分范围：[0.01, 10.0]，默认 1.0
- 仅在分数变化超过 0.01 时写入更新，减少 I/O
- 支持按单个 collection 或按 agent_id 全量重算

**评分语义**：
- 被检索且被多次采纳的知识 → 高分（最高10.0）
- 被检索但从未采纳的知识 → adoptRatio=0，评分随时间衰减
- 新入库的知识 → ageFactor≈1.0，初始评分1.0

### 4.4 Embedding 服务 (Embedder + LLMClient)

- 通过 OpenAI 兼容 API 调用 Embedding 模型
- **LRU 缓存**：最多 10000 条，避免重复向量化
- **批量向量化**：自动分离缓存命中/未命中，未命中部分按批次（默认20条）请求
- **熔断保护**：5次连续失败后熔断，30秒后半开探测
- **重试机制**：指数退避重试，最多2次，base 500ms，不重试 4xx 错误
- **请求超时**：可配置，默认30秒

### 4.5 Milvus 管理 (MilvusManager)

- **自动连接/重连**：启动时连接，断开后每5秒尝试重连
- **集合自动创建**：首次写入时自动创建集合、索引并加载到内存
- **集合命名**：`kb_{agent_id}_{collection_name}`，通过前缀隔离不同 Agent
- **重启恢复**：检测已有集合是否已加载到内存，未加载则自动加载

## 五、数据模型

### 5.1 Milvus Collection Schema

Collection 命名规则：`kb_{agent_id}_{collection_name}`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | VarChar(64) | 主键 | hash 生成 |
| content | VarChar(65535) | | 文本内容 |
| vector | FloatVector(dim) | | 内容向量，dim 由配置决定 |
| question | VarChar(4096) | | 问题文本（QA chunk 有值，其他为空） |
| question_vector | FloatVector(dim) | | 问题向量（QA chunk 有值，其他为零向量） |
| source | VarChar(512) | | 来源标识 |
| title | VarChar(256) | | 文档标题 |
| chunk_index | Int64 | | 块序号 |
| content_hash | VarChar(64) | | 内容哈希（去重用） |
| created_at | Int64 | | 创建时间戳 |
| ingest_time | Int64 | | 入库时间戳（评分用） |
| hit_count | Int64 | | 检索命中次数 |
| adopt_count | Int64 | | 被采纳次数 |
| relevance_score | Float | | 动态相关性评分 |
| chunk_type | VarChar(16) | | 块类型：`prose` / `qa` |

### 5.2 索引

| 字段 | 索引类型 | 度量方式 | 参数 |
|------|---------|---------|------|
| vector | IVF_FLAT | COSINE | nlist=1024 |
| question_vector | IVF_FLAT | COSINE | nlist=1024 |

### 5.3 检索参数

| 参数 | 值 | 说明 |
|------|---|------|
| nprobe | 16 | 搜索时探测的聚类数 |

## 六、中间件与基础设施

### 6.1 认证 (Auth)

- 方式：请求头 `x-api-key` 或 query 参数 `api_key`
- 未配置 API_KEY 时跳过认证（向后兼容）
- 认证失败返回 401

### 6.2 参数校验 (Validation)

- 所有写入端点均有参数校验
- 校验规则：类型检查、长度限制、正则匹配（agent_id 和 collection 仅允许 `[a-zA-Z0-9_-]`）
- 文本最大 5MB，topK 最大 50，collection 名最大 128 字符，agent_id 最大 64 字符

### 6.3 限流 (Rate Limiter)

- ingest 端点：30次/分钟/IP
- 滑动窗口算法
- 超限返回 429 + `retryAfterMs`

### 6.4 请求追踪

- 自动生成 `X-Request-ID`（UUID v4），支持请求头传入
- 响应头回传 `X-Request-ID`
- 错误日志关联 requestId

### 6.5 安全

- 安全响应头：`X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`X-XSS-Protection: 1; mode=block`
- 错误脱敏：5xx 错误自动过滤内部 IP、端口、服务名等敏感信息
- Milvus 过滤器注入防护：关键词和 chunkType 值转义双引号

### 6.6 熔断器 (Circuit Breaker)

- 状态机：closed → open → half-open → closed
- 参数：失败阈值5次，熔断30秒，半开探测1次
- 应用于 Embedding 调用

### 6.7 重试 (Retry)

- 指数退避：base × 2^attempt，上限 maxDelay
- 默认：最多3次，base 1000ms，上限 10000ms
- Embedding 调用：最多2次，base 500ms
- 不重试 4xx 客户端错误和 AbortError

### 6.8 优雅关闭

- 监听 SIGTERM / SIGINT
- 停止接受新连接
- 清理 Milvus 重连定时器
- 10秒超时强制退出

## 七、配置项

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| MILVUS_ADDRESS | localhost:19530 | Milvus 连接地址 |
| MILVUS_USERNAME | 空 | Milvus 用户名 |
| MILVUS_PASSWORD | 空 | Milvus 密码 |
| LLM_BASE_URL | http://127.0.0.1:1234/v1 | LLM 服务地址（当前仅用于配置，未实际调用） |
| LLM_API_KEY | 空 | LLM API 密钥 |
| LLM_MODEL | qwen/qwen3.5-9b | LLM 模型名 |
| EMBEDDING_BASE_URL | http://127.0.0.1:1234/v1 | Embedding API 地址 |
| EMBEDDING_API_KEY | 空 | Embedding API 密钥 |
| EMBEDDING_MODEL | text-embedding-qwen3-embedding | 向量化模型名 |
| EMBEDDING_DIMENSION | 2560 | 向量维度 |
| PORT | 3001 | 服务端口 |
| API_KEY | 空（不启用） | API 认证密钥 |
| CORS_ORIGIN | 空（不允许） | CORS 允许来源，逗号分隔 |
| REQUEST_TIMEOUT_MS | 30000 | 请求超时（毫秒） |
| LOG_LEVEL | info | 日志级别 |

## 八、外部依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| @zilliz/milvus2-sdk-node | ^2.4.9 | Milvus 向量数据库客户端 |
| express | ^4.18.2 | HTTP 服务框架 |
| cors | ^2.8.6 | 跨域中间件 |
| dotenv | ^16.4.5 | 环境变量加载 |
| openai | ^4.28.0 | OpenAI 兼容 API 客户端（已引入但 Embedding 使用原生 fetch） |

## 九、Java 重写注意事项

1. **Milvus Java SDK**：使用 `milvus-sdk-java`，API 与 Node SDK 有差异，需对照调整
2. **Embedding 调用**：当前使用原生 fetch 调用 OpenAI 兼容 API，Java 可用 OkHttp/WebClient 或 OpenAI Java SDK
3. **向量索引**：IVF_FLAT + COSINE，Java 端需确认 SDK 对应参数名
4. **Upsert 语义**：Milvus 的 hit_count/adopt_count 更新采用 query 全字段 + upsert 方式，Java 端需同样处理
5. **异步处理**：TypeScript 中 hit_count 更新为 fire-and-forget，Java 可用 @Async 或 CompletableFuture
6. **熔断/限流**：Java 可用 Resilience4j 替代自实现的 CircuitBreaker 和 RateLimiter
7. **分块逻辑**：中英文语句分割和 QA 格式检测需在 Java 中重新实现，注意正则兼容性
8. **Collection 命名**：`kb_{agent_id}_{collection_name}` 规则需保持一致，确保数据兼容
