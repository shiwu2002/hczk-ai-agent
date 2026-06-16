# 桓宸智科 AI 平台 — 智能体接入规范

> 版本：4.1.0
> 日期：2026-06-17
> 适用范围：所有需要注册到桓宸智科 AI 平台的容器智能体
>
> **v4.1.0 变更**：统一工具执行参数为 `user_id`（雪花ID），文档上传表单字段 `merchant_id` 改为 `user_id`，明确前端用户选择必须绑定 `userId`（雪花ID）而非 `id`（自增主键）。

---

## 1. 概述

桓宸智科 AI 平台采用**智能体注册中心**架构。每个智能体作为独立服务部署在容器中，通过实现平台规定的标准接口注册到平台。平台负责：

- **健康监测**：定期调用智能体的健康检测接口，实时展示在线/离线/降级状态
- **对话路由**：将用户对话请求路由到智能体的对话接口（同步/流式）
- **能力展示**：通过元信息接口获取智能体的能力标签、工具列表、模型信息
- **会话管理**：代理获取会话历史、清除会话
- **文档管理**：将知识库文档上传到智能体的文档接口（可选）
- **统一管理**：在管理界面以卡片形式展示所有智能体状态和能力
- **JWT 鉴权**：平台调用智能体时使用 JWT Token 传递身份信息，智能体通过共享密钥验证
- **MCP 工具调用**：平台为智能体提供可调用的工具组（Skills），智能体按需发现和执行工具

## 2. 接口规范总览

| 接口 | 方法 | 建议路径 | 必填 | 优先级 | 说明 |
|------|------|---------|------|--------|------|
| 健康检测 | GET | `/api/health` | **必填** | P0 | 平台定期调用，检测在线状态 |
| 对话 | POST | `/api/chat` | **必填** | P0 | 同步对话，返回完整回复 |
| 流式对话 | POST | `/api/chat/stream` | 可选 | P0 | SSE 格式流式输出 |
| 智能体元信息 | GET | `/api/agent/info` | 可选 | P0 | 返回能力、工具、模型信息 |
| 会话历史 | GET | `/api/chat/history/{session_id}` | 可选 | P1 | 获取指定会话历史消息 |
| 清除会话 | DELETE | `/api/chat/sessions/{session_id}` | 可选 | P2 | 清除指定会话 |
| 文档上传 | POST | `/api/documents` | 可选 | P1 | 上传知识库文档 |

---

## 3. JWT 鉴权机制

### 3.1 概述

平台调用智能体所有接口时，通过 `Authorization` 请求头传递 JWT Token，不再明文传递 API Key。智能体端使用与平台共享的签名密钥验证 JWT 的有效性，从中提取商家身份和 API Key 信息。

**算法**：HMAC-SHA256（HS256），签名密钥为字符串经 UTF-8 编码的字节。

**适用范围**：所有业务接口（对话、流式对话、会话历史、清除会话、文档上传）均需验证 JWT。健康检测和元信息接口不需要 JWT 验证（仍使用注册时配置的 `auth_header`，如已配置）。

### 3.2 JWT Payload 结构

```json
{
  "sub": "user_1234567890123456789",
  "iss": "hczk-platform",
  "iat": 1781593200,
  "exp": 1781596800,
  "user_id": "1234567890123456789",
  "scope": "chat",
  "apiKey": "sk-hczk-xxx"
}
```

**字段说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `sub` | string | 是 | 主体标识，格式为 `user_{user_id}` |
| `iss` | string | 是 | 签发者，固定为 `hczk-platform` |
| `iat` | number | 是 | 签发时间（Unix 时间戳，秒级） |
| `exp` | number | 是 | 过期时间（Unix 时间戳，秒级），默认1小时 |
| `user_id` | string | 是 | 用户 ID（雪花ID），用于用户隔离和知识库查找 |
| `scope` | string | 是 | 权限范围，当前固定为 `chat` |
| `apiKey` | string | 否 | 平台 API Key（访问知识库和模型时使用），试用场景为空 |

### 3.3 请求头格式

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdW...省略...xxx
```

> 注意：JWT 方式始终带有 `Bearer ` 前缀。

### 3.4 共享密钥配置

平台和智能体端必须配置相同的 HMAC-SHA256 签名密钥：

**平台端配置**

| 环境 | 路径 | 说明 |
|------|------|------|
| 开发环境 | `application-dev.yaml` → `jwt.secret` | 默认值为 `hczk-ai-platform-secret-key-2024-very-long-and-secure` |
| 生产环境 | 环境变量 `JWT_AGENT_SHARED_SECRET` | 若未设置则回退到 `JWT_SECRET` |

**智能体端配置**

| 环境 | 配置方式 | 示例值 |
|------|---------|--------|
| 开发环境 | 硬编码或配置文件 | `hczk-ai-platform-secret-key-2024-very-long-and-secure` |
| 生产环境 | 环境变量 `JWT_AGENT_SHARED_SECRET` | 与平台保持一致 |

**相关配置项**

| 配置项 | 默认值 | 说明 |
|------|--------|------|
| `jwt.agent.shared-secret` | 复用 `jwt.secret` | 智能体调用 JWT 的签名密钥 |
| `jwt.agent.expiration` | `3600000`（1 小时） | Token 过期时间（毫秒） |
| `jwt.agent.issuer` | `hczk-platform` | JWT 签发者标识 |

### 3.5 智能体端验证流程

```
1. 从 Authorization 请求头提取 Bearer Token
   - 检查格式：必须以 "Bearer " 开头
   - 截取 "Bearer " 之后的部分作为 JWT Token

2. 使用共享密钥验证 JWT 签名
   - 算法：HS256
   - 密钥：共享密钥字符串的 UTF-8 字节

3. 检查 iss（签发者）== "hczk-platform"
   - 防止其他来源的 JWT 被误接受

4. 检查 exp（过期时间）未过期
   - 建议允许 30 秒时钟偏差

5. 从 payload 中提取 user_id 和 apiKey
   - user_id 用于用户隔离和知识库查找（必填）
   - apiKey 用于调用平台服务（可空）

6. 使用 user_id 做用户隔离，并根据 user_id 查找用户的知识库集合
7. 使用 apiKey 调用平台知识库/模型服务（如需要）
```

**验证失败时的响应**

| 失败原因 | HTTP 状态码 | 响应体示例 |
|---------|------------|-----------|
| 缺少认证信息 | 401 | `{"error": "缺少认证信息"}` |
| JWT 签名无效 | 401 | `{"error": "JWT 验证失败: 签名无效"}` |
| 签发者不匹配 | 401 | `{"error": "JWT 验证失败: 无法识别的签发者"}` |
| Token 已过期 | 401 | `{"error": "JWT 验证失败: Token 已过期"}` |

### 3.6 试用场景

管理后台试用智能体时，平台生成特殊的 JWT Token：

- `user_id` 为 `"trial"`
- `apiKey` 为空（不包含该字段）
- `sub` 为 `"user_trial"`

```json
{
  "sub": "user_trial",
  "iss": "hczk-platform",
  "user_id": "trial",
  "scope": "chat"
}
```

智能体端应识别 `user_id === "trial"` 为试用请求，可限制功能或返回示例数据。

### 3.7 代码示例

**Java 生成（平台端，JJWT 0.12.x）**

```java
SecretKey key = Keys.hmacShaKeyFor(sharedSecret.getBytes(StandardCharsets.UTF_8));
String token = Jwts.builder()
    .subject("user_" + userId)
    .issuer("hczk-platform")
    .issuedAt(new Date())
    .expiration(new Date(System.currentTimeMillis() + 3600000))
    .claim("user_id", userId)
    .claim("scope", "chat")
    .claim("apiKey", apiKey)  // 可选
    .signWith(key)
    .compact();
```

**Node.js 验证（智能体端，jsonwebtoken）**

```js
const jwt = require('jsonwebtoken');
const payload = jwt.verify(token, SHARED_SECRET, {
    algorithms: ['HS256'],
    issuer: 'hczk-platform',
    clockTolerance: 30
});
```

**Python 验证（智能体端，PyJWT）**

```python
import jwt
payload = jwt.decode(token, SHARED_SECRET, algorithms=['HS256'],
                     issuer='hczk-platform', leeway=30)
```

---

## 4. MCP 工具调用（Skills）

### 4.1 概述

平台为智能体提供了 **MCP（Model Context Protocol）风格的工具调用机制**。工具按**工具组（Skill）** 组织，每个工具组包含若干可被 function calling 调用的工具（如知识库检索、文本摄入等）。

**特点**：
- **渐进加载**：对话时只传递工具组目录，智能体按需查询工具详情
- **统一执行**：内置工具统一走 `POST /api/tools/execute`，API 类工具走自定义端点
- **JWT 鉴权**：所有工具 API 复用对话时的 JWT Token

### 4.2 工具组与工具的关系

```
Skill（工具组）                  ToolDefinition（工具）
┌────────────────────┐          ┌──────────────────────────────┐
│ name: "knowledge"   │          │ name: "knowledge_search"      │
│ displayName: "知识库"│───1:N──▶│ description: "检索知识库..."    │
│ tool_count: 5       │          │ inputSchema: {type, params}   │
└────────────────────┘          │ endpoint: /api/tools/execute   │
                                └──────────────────────────────┘
```

### 4.3 完整调用示例

以下是智能体一次完整对话中使用工具的典型流程：

```
1. 平台 → 智能体：POST /api/chat（或 /api/chat/stream）
   {
     "message": "帮我查一下产品价格",
     "session_id": "abc123",
     "user_id": "1234567890123456789",
     "available_skills": [
       {"name": "knowledge", "display_name": "知识库", "description": "...", "tool_count": 5}
     ],
     "tools_discovery_endpoint": "http://平台地址:8080/api/tools/group"
   }

2. 智能体分析用户意图 → 需要检索知识库 → 查询工具详情
   GET http://平台地址:8080/api/tools/group/knowledge
   Authorization: Bearer {jwt_token}
   
   ← 返回 knowledge 组下 5 个工具的完整定义（name, description, parameters, endpoint）

3. 智能体将工具注册为 function，LLM 决定调用 knowledge_search
   → 智能体发送 HTTP 请求执行工具：
   
   POST http://平台地址:8080/api/tools/execute
   Authorization: Bearer {jwt_token}
   {
     "tool_name": "knowledge_search",
     "arguments": {
       "query": "产品价格",
       "collection_name": "products",
       "user_id": "1234567890123456789"
     }
   }

   ← 平台返回：
   { "code": 200, "data": { "success": true, "chunks": [...], "total": 3 } }

4. 智能体将检索结果作为上下文，LLM 生成最终回复
   → SSE: data: {"type":"content","content":"根据知识库检索..."}
```

### 4.4 智能体端实现要点

**Step 1 — 接收工具组目录**

对话请求中的 `available_skills` 和 `tools_discovery_endpoint` 是可选的。如果不存在（或为空数组），说明该商户没有可用工具，跳过工具调用逻辑即可。

**Step 2 — 按需查询工具**

不要一收到请求就查询所有工具组。根据用户意图判断是否需要工具，例如用户问"查一下"、"帮我搜索"时才去查询 `knowledge` 组。

```javascript
// 仅当需要时查询
if (userIntentNeedsKnowledge && req.body.tools_discovery_endpoint) {
  const url = req.body.tools_discovery_endpoint + '/knowledge';
  const tools = await fetch(url, {
    headers: { 'Authorization': req.headers.authorization }
  }).then(r => r.json());
  
  // tools = [{type:"function", function:{name,description,parameters}, endpoint}, ...]
  // 注册进 LLM 的 tool registry
}
```

**Step 3 — 执行工具**

每个工具返回的 `endpoint` 字段即执行地址。内置工具指向 `http://平台/api/tools/execute`，API 类工具指向各自的外部 URL。

```javascript
const tool = tools.find(t => t.function.name === 'knowledge_search');
const result = await fetch(tool.endpoint, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': req.headers.authorization  // 复用 JWT
  },
  body: JSON.stringify({
    tool_name: 'knowledge_search',
    arguments: { query: '...', collection_name: 'products', user_id: '1234567890123456789' }
  })
}).then(r => r.json());
// result.data 中包含工具执行结果
```

**Step 4 — 使用结果**

将 `result.data` 中的内容注入到 LLM 的上下文中，由 LLM 生成最终回复。如果工具返回 `{success: false}`，告知用户操作失败。

### 4.4 工具发现 API

#### 4.4.1 获取工具组目录

```
GET /api/tools/groups
```

智能体收到 `tools_discovery_endpoint` 后，后追加具体工具组名即可（如 + "/knowledge"）。

**响应**

```json
[
  {
    "name": "knowledge",
    "display_name": "知识库",
    "description": "知识库相关操作工具组，包含检索、摄入、文件上传等工具",
    "category": "knowledge",
    "tool_count": 5
  }
]
```

#### 4.4.2 获取工具组内工具详情

```
GET /api/tools/group/{skill_name}
Authorization: Bearer {jwt_token}
```

**响应**（LLM function_call 格式）

```json
[
  {
    "type": "function",
    "function": {
      "name": "knowledge_search",
      "description": "从知识库中检索与查询相关的文档内容。使用混合检索策略（向量检索+关键词检索），返回最相关的文档分块及其相关性得分。",
      "parameters": {
        "type": "object",
        "properties": {
          "query": {"type": "string", "description": "检索查询文本"},
          "collection_name": {"type": "string", "description": "知识库集合名称"},
          "top_k": {"type": "integer", "description": "返回结果数量，默认5", "default": 5}
        },
        "required": ["query", "collection_name"]
      }
    },
    "endpoint": "http://平台地址:8080/api/tools/execute"
  }
]
```

返回字段说明

| 字段 | 说明 |
|------|------|
| `type` | 固定为 `"function"` |
| `function.name` | 工具唯一名称，执行时使用 |
| `function.description` | 工具功能描述，LLM 据此判断是否调用 |
| `function.parameters` | JSON Schema 格式的输入参数定义 |
| `endpoint` | 工具执行端点（内置工具为 `/api/tools/execute`，API 工具为自定义 URL） |

### 4.5 工具执行 API

```
POST /api/tools/execute
Content-Type: application/json
Authorization: Bearer {jwt_token}
```

**请求体**

```json
{
  "tool_name": "knowledge_search",
  "arguments": {
    "query": "产品价格",
    "collection_name": "products",
    "user_id": "1234567890123456789"
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `tool_name` | string | 是 | 工具名称（与 function.name 一致） |
| `arguments` | object | 是 | 工具参数，与 inputSchema 对应 |
| `arguments.user_id` | string | 建议 | 用户雪花ID（直接从 JWT 的 `user_id` claim 获取），用于定位该用户的 Milvus 物理集合 |

**成功响应**

```json
{
  "code": 200,
  "data": {
    "success": true,
    "chunks": [
      {"content": "产品价格为...", "score": 0.95, "source": "products.md"},
      {"content": "促销活动...", "score": 0.87, "source": "promotion.md"}
    ],
    "total": 2,
    "strategy": "hybrid"
  }
}
```

**错误响应**

```json
{
  "code": 200,
  "data": {
    "success": false,
    "error": "检索失败: 集合不存在"
  }
}
```

### 4.6 内置知识库工具

应用启动时自动注册到 `knowledge` 工具组，无需手动创建。

| 工具名称 | 显示名 | 功能 | 关键参数 |
|---------|--------|------|---------|
| `knowledge_search` | 知识库检索 | 混合检索返回相关分块 | query, collection_name, top_k |
| `knowledge_ingest` | 知识库文本摄入 | 文本分块+向量化入 Milvus | text, collection_name, user_id |
| `knowledge_ingest_file` | 知识库文件上传 | 上传文档文件入知识库 | file_name, collection_name |
| `knowledge_list_collections` | 列出知识库集合 | 列出所有集合及基本信息 | user_id |
| `knowledge_get_chunks` | 查看知识分块 | 分页浏览集合内的分块 | collection_name, user_id |

### 4.7 知识库集合命名规则

平台使用 Milvus 向量数据库存储知识库，物理集合名按以下规则拼接：

```
kb_{user_id}_{collection_name}
```

| 组成部分 | 说明 | 示例 |
|---------|------|------|
| `kb_` | 固定前缀 | — |
| `{user_id}` | 用户雪花ID（纯数字） | `1234567890123456789` |
| `{collection_name}` | 逻辑集合名，默认 `default` | `default`、`products` |

**示例**：用户 `1234567890123456789` 上传文档到 `products` 集合 → Milvus 物理集合名为 `kb_1234567890123456789_products`

**智能体检索知识库时**：
- `user_id` 参数直接传 JWT 中的雪花ID（纯数字字符串），无需任何前缀
- `collection_name` 传逻辑集合名（如 `default`、`products`）
- 平台自动拼接为 `kb_{user_id}_{collection_name}` 查询 Milvus

**前端用户选择注意事项**：

平台管理后台的用户选择下拉框（知识库管理、检索测试、评分重算等场景）必须绑定 `User.userId`（雪花ID 字符串），**不能**绑定 `User.id`（users 表自增主键）。二者均为唯一标识但取值不同：

| 字段 | 类型 | 来源 | 用途 |
|------|------|------|------|
| `User.id` | Long | users 表自增主键 | 平台内部关联（如绑定表外键） |
| `User.userId` | String | 雪花算法生成 | 对外暴露的用户标识，用于 JWT、Milvus 集合命名、工具参数 |

若前端误绑 `User.id`，会导致 `agentId` 传成自增ID（如 `5`），Milvus 集合名拼成 `kb_5_default`，而实际数据存储在 `kb_{雪花ID}_default` 中，从而检索不到数据。

`/knowledge/owners/users` 接口返回的用户对象同时包含 `id` 和 `userId` 两个字段，前端取用时务必使用 `userId`。

---

## 5. 接口详细规范

### 5.1 健康检测接口（必填）

**请求**

```
GET {health_endpoint}
```

> 健康检测接口不携带 JWT 认证头。如果注册时配置了 `auth_header`，平台仍会携带该认证头用于兼容旧版智能体。

**响应**

```json
{
  "status": "ok",
  "version": "1.2.0",
  "uptime": 86400,
  "timestamp": "2026-06-16T10:30:00Z",
  "components": {
    "database": {"status": "connected", "latency_ms": 5},
    "knowledge": {"status": "connected", "collection": "products", "document_count": 1234},
    "llm": {"status": "available", "model": "deepseek-chat"}
  },
  "runtime": {"node_version": "v20.11.0", "memory_usage_mb": 256, "active_sessions": 12}
}
```

**字段说明**：见上表，响应格式灵活，智能体可自定义 `components` 和 `runtime` 结构。

**状态判定规则**

| status 值 | 平台显示 | 含义 |
|-----------|---------|------|
| `ok` | 在线（绿色） | 服务完全正常 |
| `degraded` | 降级（黄色） | 部分功能受限但仍可工作 |
| 其他值 / 请求超时 | 离线（红色） | 服务不可用 |

**超时要求**：接口应在 **3 秒内** 返回响应，否则平台判定为离线。

---

### 5.2 对话接口（必填）

**请求**

```
POST {chat_endpoint}
Content-Type: application/json
Authorization: Bearer {jwt_token}
```

**请求体**

```json
{
  "message": "你好，请问有什么产品推荐？",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "user_id": "1234567890123456789",
  "collection_name": "products",
  "context": [
    {"role": "user", "content": "之前的对话内容"},
    {"role": "assistant", "content": "之前的回复内容"}
  ],
  "available_skills": [
    {"name": "knowledge", "display_name": "知识库", "description": "知识库操作工具组", "tool_count": 5}
  ],
  "tools_discovery_endpoint": "http://平台地址:8080/api/tools/group"
}
```

**新增字段说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `user_id` | string | 是 | 用户ID（雪花ID），智能体据此查找用户的知识库集合 |
| `available_skills` | array | 否 | 可用工具组目录，每个条目含 name、display_name、description、tool_count |
| `tools_discovery_endpoint` | string | 否 | 工具详情查询端点，追加具体工具组名（如 `/knowledge`）后 GET 请求获取该组所有工具 |

其余字段（`message`、`session_id` 等）保持不变。

**响应**

```json
{
  "reply": "根据您的需求，我推荐以下产品...",
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "metadata": {
    "input_tokens": 150,
    "output_tokens": 320,
    "model": "deepseek-chat",
    "tool_calls": 2,
    "retrieval_count": 3,
    "thinking_content": "用户询问产品推荐，需要检索知识库...",
    "retrieval_used": true,
    "fallback_used": false
  }
}
```

**响应字段说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `reply` | string | **必填** | 智能体回复内容 |
| `session_id` | string | 可选 | 会话 ID（建议返回） |
| `metadata` | object | 可选 | 元数据，结构自定义 |
| `metadata.input_tokens` | number | 可选 | 输入 Token 数（用于计费统计） |
| `metadata.output_tokens` | number | 可选 | 输出 Token 数（用于计费统计） |
| `metadata.thinking_content` | string | 可选 | AI 思考过程（启用思考模式时返回） |
| `metadata.retrieval_used` | boolean | 可选 | 是否使用了知识库检索 |
| `metadata.fallback_used` | boolean | 可选 | 是否使用了降级回复 |

**错误响应**

```json
{
  "error": "LLM 服务暂时不可用",
  "code": "LLM_UNAVAILABLE",
  "session_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

### 5.3 流式对话接口（可选，P0 优先级）

**请求**

```
POST {stream_endpoint}
Content-Type: application/json
Authorization: Bearer {jwt_token}
```

**请求体**：与 `POST /api/chat` 完全一致（含 `available_skills` 和 `tools_discovery_endpoint`）。

**响应**：`Content-Type: text/event-stream`，每行格式 `data: {JSON}\n\n`

> **重要**：每个 SSE 事件必须以 `\n\n`（两个换行符）结尾，否则多事件会被拼接在一起。

**事件类型**

| type | 说明 |
|------|------|
| `thinking` | 思考/意图识别过程 |
| `retrieval` | 知识库检索 |
| `tool` | 工具调用 |
| `content` | 回复内容片段 |
| `done` | 结束 |

**降级策略**：如果智能体未配置 `stream_endpoint`，平台自动降级为同步调用。

---

### 5.4 智能体元信息接口（可选，P0 优先级）

**请求**

```
GET {info_endpoint}
```

> 元信息接口不携带 JWT 认证头，平台直接调用。

**响应**

```json
{
  "name": "客服智能体",
  "version": "1.0.0",
  "description": "支持订单查询、商品推荐、工单创建的客服智能体",
  "capabilities": {
    "knowledge_retrieval": true,
    "tool_calling": true,
    "multi_turn": true,
    "streaming": true,
    "thinking": true
  },
  "model": "deepseek-v4-pro",
  "tools": [
    { "name": "query_order", "description": "查询订单状态" },
    { "name": "search_product", "description": "搜索商品信息" },
    { "name": "create_ticket", "description": "创建工单" }
  ]
}
```

**字段说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 智能体显示名称 |
| `version` | string | 否 | 版本号 |
| `description` | string | 否 | 功能描述 |
| `capabilities` | object | 否 | 能力标签，平台据此展示功能标签 |
| `capabilities.knowledge_retrieval` | boolean | 否 | 是否支持知识库检索 |
| `capabilities.tool_calling` | boolean | 否 | 是否支持工具调用 |
| `capabilities.multi_turn` | boolean | 否 | 是否支持多轮对话 |
| `capabilities.streaming` | boolean | 否 | 是否支持流式输出 |
| `capabilities.thinking` | boolean | 否 | 是否支持思考模式 |
| `model` | string | 否 | 使用的 LLM 模型 |
| `tools` | array | 否 | 可用工具列表 |
| `tools[].name` | string | 是 | 工具名称 |
| `tools[].description` | string | 否 | 工具描述 |

**平台使用场景**：
1. 智能体卡片展示能力标签：`知识库` `工具调用` `流式输出`
2. 展示可用工具列表
3. 展示模型信息

**降级策略**：如果未配置 `info_endpoint`，平台从数据库中读取注册时填写的基本信息。

---

### 5.5 会话历史接口（可选，P1 优先级）

**请求**

```
GET {history_endpoint}/{session_id}
Authorization: Bearer {jwt_token}
```

**响应**

```json
{
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "messages": [
    { "role": "user", "content": "我的订单到哪了" },
    { "role": "assistant", "content": "您的订单已发货，预计明天到达。" },
    { "role": "user", "content": "可以退货吗" },
    { "role": "assistant", "content": "可以的，请在7天内申请退货。" }
  ],
  "message_count": 4
}
```

**字段说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `session_id` | string | 是 | 会话 ID |
| `messages` | array | 是 | 消息列表，按时间顺序 |
| `messages[].role` | string | 是 | 角色：`user` / `assistant` / `system` |
| `messages[].content` | string | 是 | 消息内容 |
| `message_count` | number | 否 | 消息总数 |

---

### 5.6 清除会话接口（可选，P2 优先级）

**请求**

```
DELETE {history_endpoint}/{session_id}
Authorization: Bearer {jwt_token}
```

**响应**

```json
{
  "status": "ok",
  "session_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

> 注意：清除会话复用 `history_endpoint` 地址，通过 HTTP 方法区分 GET（获取历史）和 DELETE（清除会话）。

---

### 5.7 文档上传接口（可选，P1 优先级）

**请求**

```
POST {document_endpoint}
Content-Type: multipart/form-data
Authorization: Bearer {jwt_token}
```

**表单字段**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | File | **必填** | 上传的文档文件 |
| `collection_name` | string | **必填** | 知识库集合名称 |
| `user_id` | string | 可选 | 用户雪花ID，用于定位该用户的 Milvus 物理集合 |
| `document_type` | string | 可选 | 文档类型：`pdf`/`txt`/`md`/`docx` 等 |

**响应**

```json
{
  "status": "ok",
  "document_id": "doc_abc123",
  "collection_name": "products",
  "chunk_count": 42,
  "message": "文档已成功入库"
}
```

---

## 6. 注册流程

### 6.1 管理员注册智能体

1. 在平台管理后台「智能体管理」页面点击「注册智能体」
2. 填写以下信息：

**基本信息**

| 字段 | 必填 | 说明 |
|------|------|------|
| 智能体名称 | 是 | 如"客服智能体" |
| 描述 | 否 | 功能描述 |
| 类型标识 | 否 | 自定义标识，如 `customer_service`、`sales` |
| 版本号 | 否 | 如 `1.0.0` |

**必填接口**

| 字段 | 必填 | 说明 |
|------|------|------|
| 健康检测接口 | 是 | 如 `http://agent-host:3000/api/health` |
| 对话接口 | 是 | 如 `http://agent-host:3000/api/chat` |

**可选接口（增强功能）**

| 字段 | 必填 | 说明 |
|------|------|------|
| 流式对话接口 | 否 | 如 `http://agent-host:3000/api/chat/stream` |
| 智能体元信息接口 | 否 | 如 `http://agent-host:3000/api/agent/info` |
| 会话历史接口 | 否 | 如 `http://agent-host:3000/api/chat/history` |
| 文档上传接口 | 否 | 如 `http://agent-host:3000/api/documents` |

**旧版兼容配置**

| 字段 | 必填 | 说明 |
|------|------|------|
| 认证头 | 否 | 如 `Bearer sk-xxx`，仅用于健康检测和元信息接口的旧版兼容 |

> **注意**：业务接口（对话、流式对话等）的鉴权已统一使用 JWT Token，不再依赖注册时配置的 `auth_header`。`auth_header` 仅用于兼容旧版智能体的健康检测和元信息接口。新注册的智能体无需配置此项。工具调用功能由平台在请求中自动传递 `available_skills` 和 `tools_discovery_endpoint`，智能体端无需特殊配置。

3. 点击「注册」，平台立即调用健康检测接口验证可达性
4. 注册成功后，智能体卡片显示在管理界面，能力标签和工具列表通过元信息接口自动获取

### 6.2 绑定用户

在智能体管理页面，将用户绑定到已注册的智能体。绑定只需选择用户，平台会将该用户通过 API Key 发起的对话请求路由到此智能体。绑定以 user_id（雪花ID）为唯一标识，一个用户只能绑定一个智能体。

### 6.3 配置共享密钥

智能体端需要配置与平台相同的 JWT 签名密钥，用于验证平台签发的 JWT Token：

- **开发环境**：与平台使用相同的密钥字符串
- **生产环境**：通过环境变量 `JWT_AGENT_SHARED_SECRET` 注入，与平台保持一致

---

## 7. 平台行为说明

### 7.1 健康监测

- 平台每 **30 秒** 自动调用所有智能体的健康检测接口
- 检测超时时间为 **3 秒**

### 7.2 对话路由

- 用户通过 API Key 发起对话请求 → 平台查询用户绑定（按 user_id） → 获取智能体 → 生成 JWT Token + 工具组目录 → 调用智能体的 `chat_endpoint`
- 路由优先级：`agent_id` > `agent_endpoint`

### 7.3 工具调用

- 平台传递工具组目录而非全部工具，智能体按需查询
- 内置工具走 `POST /api/tools/execute`
- API 类工具走自定义 endpoint
- 工具组目录和工具定义均通过 Redis 缓存（20-30 分钟随机 TTL）

### 7.4 JWT 鉴权

- 每次调用生成新 Token，有效期 1 小时
- 试用场景 `user_id` 为 `"trial"`，`apiKey` 为空
- 工具执行和发现 API 复用同一 JWT

---

## 8. 接入示例

### 8.1 Node.js (Express) 示例

```javascript
const express = require('express');
const jwt = require('jsonwebtoken');
const app = express();
app.use(express.json());

const SHARED_SECRET = process.env.JWT_AGENT_SHARED_SECRET || 'hczk-ai-platform-secret-key-2024-very-long-and-secure';

function verifyPlatformJWT(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: '缺少认证信息' });
  }
  try {
    const token = authHeader.substring(7);
    const payload = jwt.verify(token, SHARED_SECRET, { issuer: 'hczk-platform' });
    req.user = {
      id: payload.user_id,
      apiKey: payload.apiKey || null,
      isTrial: payload.user_id === 'trial'
    };
    next();
  } catch (err) {
    return res.status(401).json({ error: 'JWT 验证失败: ' + err.message });
  }
}

// 健康检测（无需认证）
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', version: '1.0.0', uptime: process.uptime(), timestamp: new Date().toISOString() });
});

// 对话接口（需 JWT 认证，接收 tools_discovery_endpoint）
app.post('/api/chat', verifyPlatformJWT, async (req, res) => {
  const { message, session_id, user_id, available_skills, tools_discovery_endpoint } = req.body;
  const { apiKey, isTrial } = req.user;

  // 如果智能体需要调用平台工具：
  // 1. 查询工具组详情
  //    GET tools_discovery_endpoint + '/knowledge'
  // 2. 执行工具
  //    POST {返回的 endpoint}  body: {tool_name, arguments}
  // 3. 使用 JWT Token 鉴权（从 req.headers.authorization 获取）

  const reply = await processMessage(message, session_id, user_id, apiKey);
  res.json({ reply, session_id, metadata: { model: 'deepseek-chat' } });
});

// 流式对话接口
app.post('/api/chat/stream', verifyPlatformJWT, async (req, res) => {
  const { available_skills, tools_discovery_endpoint } = req.body;

  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');

  res.write(`data: ${JSON.stringify({ type: 'thinking', content: '意图识别中...' })}\n\n`);
  res.write(`data: ${JSON.stringify({ type: 'content', content: '您好！' })}\n\n`);
  res.write(`data: ${JSON.stringify({ type: 'done' })}\n\n`);
  res.end();
});

// 智能体元信息（无需认证）
app.get('/api/agent/info', (req, res) => {
  res.json({
    name: '客服智能体', version: '1.0.0',
    capabilities: { knowledge_retrieval: true, tool_calling: true, streaming: true },
    model: 'deepseek-chat',
    tools: [{ name: 'query_order', description: '查询订单状态' }]
  });
});

app.listen(3000, () => console.log('Agent running on port 3000'));
```

### 8.2 Python (FastAPI) 示例

```python
from fastapi import FastAPI, Depends, HTTPException, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
import jwt, httpx, os, time, json, asyncio

app = FastAPI()
SHARED_SECRET = os.environ.get("JWT_AGENT_SHARED_SECRET", "hczk-ai-platform-secret-key-2024-very-long-and-secure")

class ChatRequest(BaseModel):
    message: str
    session_id: str
    user_id: str
    collection_name: str | None = None
    context: list | None = None
    available_skills: list | None = None
    tools_discovery_endpoint: str | None = None

def verify_platform_jwt(request: Request):
    auth_header = request.headers.get("Authorization", "")
    if not auth_header.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="缺少认证信息")
    token = auth_header[7:]
    try:
        payload = jwt.decode(token, SHARED_SECRET, algorithms=["HS256"], issuer="hczk-platform")
        return {"user_id": payload["user_id"], "api_key": payload.get("apiKey"),
                "is_trial": payload["user_id"] == "trial", "token": token}
    except jwt.InvalidTokenError as e:
        raise HTTPException(status_code=401, detail=f"JWT 验证失败: {e}")

@app.get("/api/health")
async def health():
    return {"status": "ok", "version": "1.0.0", "uptime": int(time.time() - start_time)}

@app.post("/api/chat")
async def chat(req: ChatRequest, user=Depends(verify_platform_jwt)):
    # 智能体可按需调用平台工具：
    # if req.tools_discovery_endpoint:
    #     url = req.tools_discovery_endpoint + "/knowledge"
    #     async with httpx.AsyncClient() as client:
    #         tools = await client.get(url, headers={"Authorization": f"Bearer {user['token']}"})
    #     # ... 使用 tools 进行 function calling ...
    reply = await process_message(req.message, req.session_id, req.user_id, user["api_key"])
    return {"reply": reply, "session_id": req.session_id}

@app.post("/api/chat/stream")
async def chat_stream(req: ChatRequest, user=Depends(verify_platform_jwt)):
    async def generate():
        yield f"data: {json.dumps({'type': 'thinking', 'content': '意图识别中...'})}\n\n"
        yield f"data: {json.dumps({'type': 'content', 'content': '您好！'})}\n\n"
        yield f"data: {json.dumps({'type': 'done'})}\n\n"
    return StreamingResponse(generate(), media_type="text/event-stream")

@app.get("/api/agent/info")
async def agent_info():
    return {"name": "客服智能体", "version": "1.0.0",
            "capabilities": {"knowledge_retrieval": True, "tool_calling": True, "streaming": True},
            "model": "deepseek-chat", "tools": [{"name": "query_order"}]}
```

---

## 9. 兼容性说明

- 所有可选接口不影响必填接口的正常工作
- 平台实现了完整的降级策略
- **JWT 鉴权**为强制要求，所有业务接口均需验证 JWT Token
- **工具调用**为可选功能，不影响不需要工具的智能体
- 健康检测和元信息接口无需 JWT 验证
- SSE 流式数据：智能体端必须确保每行以 `\n\n` 结尾

## 10. 常见问题

**Q: 智能体如何调用平台工具？**
A: 平台在对话请求中传递 `available_skills`（工具组目录）和 `tools_discovery_endpoint`（工具详情查询端点）。智能体按需查询工具详情，然后调用工具执行端点。所有工具 API 使用对话请求中的 JWT Token 鉴权。

**Q: 不实现工具调用会影响对话吗？**
A: 不会。`available_skills` 和 `tools_discovery_endpoint` 字段可忽略，不影响正常的对话功能。

**Q: 工具定义会频繁变化吗？**
A: 工具定义由平台管理员维护，变化频率低。平台对工具 API 做了 Redis 缓存（20-30 分钟随机 TTL），智能体端不需要频繁查询。

**Q: JWT Token 过期了怎么办？**
A: 平台每次调用智能体时都会生成新的 JWT Token，有效期1小时。智能体端只需验证 Token 有效性，无需处理刷新逻辑。

**Q: 智能体端如何获取共享密钥？**
A: 开发环境可直接使用与平台相同的密钥字符串；生产环境通过环境变量 `JWT_AGENT_SHARED_SECRET` 注入，与平台保持一致。

**Q: 试用场景和正式场景的 JWT 有什么区别？**
A: 试用场景下 `user_id` 为 `"trial"`，`apiKey` 为空；正式场景下为实际用户 ID 和 API Key。
