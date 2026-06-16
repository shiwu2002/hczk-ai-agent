# 桓宸智科 AI 平台 — 智能体接入规范

> 版本：2.0.0
> 日期：2026-06-16
> 适用范围：所有需要注册到桓宸智科 AI 平台的容器智能体

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

## 3. JWT 鉴权机制

### 3.1 概述

平台调用智能体所有接口时，通过 `Authorization` 请求头传递 JWT Token，不再明文传递 API Key。智能体端使用与平台共享的签名密钥验证 JWT 的有效性，从中提取商家身份和 API Key 信息。

**算法**：HMAC-SHA256（HS256），签名密钥为字符串经 UTF-8 编码的字节。

**适用范围**：所有业务接口（对话、流式对话、会话历史、清除会话、文档上传）均需验证 JWT。健康检测和元信息接口不需要 JWT 验证（仍使用注册时配置的 `auth_header`，如已配置）。

### 3.2 JWT Payload 结构

```json
{
  "sub": "merchant_M001",
  "iss": "hczk-platform",
  "iat": 1781593200,
  "exp": 1781596800,
  "merchant_id": "M001",
  "scope": "chat",
  "apiKey": "sk-hczk-xxx"
}
```

**字段说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `sub` | string | 是 | 主体标识，格式为 `merchant_{merchant_id}` |
| `iss` | string | 是 | 签发者，固定为 `hczk-platform` |
| `iat` | number | 是 | 签发时间（Unix 时间戳，秒级） |
| `exp` | number | 是 | 过期时间（Unix 时间戳，秒级），默认1小时 |
| `merchant_id` | string | 是 | 商家 ID，用于多租户隔离 |
| `scope` | string | 是 | 权限范围，当前固定为 `chat` |
| `apiKey` | string | 否 | 平台 API Key（访问知识库和模型时使用），试用场景为空 |

### 3.3 请求头格式

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdW...省略...xxx
```

> 注意：与旧版 `Authorization: {auth_header}` 格式不同，JWT 方式始终带有 `Bearer ` 前缀。

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

5. 从 payload 中提取 merchant_id 和 apiKey
   - merchant_id 用于多租户隔离（必填）
   - apiKey 用于调用平台服务（可空）

6. 使用 merchant_id 做多租户隔离
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

- `merchant_id` 为 `"trial"`
- `apiKey` 为空（不包含该字段）
- `sub` 为 `"merchant_trial"`

```json
{
  "sub": "merchant_trial",
  "iss": "hczk-platform",
  "merchant_id": "trial",
  "scope": "chat"
}
```

智能体端应识别 `merchant_id === "trial"` 为试用请求，可限制功能或返回示例数据。

### 3.7 代码示例

**Java 生成（平台端，JJWT 0.12.x）**

```java
SecretKey key = Keys.hmacShaKeyFor(sharedSecret.getBytes(StandardCharsets.UTF_8));
String token = Jwts.builder()
    .subject("merchant_" + merchantId)
    .issuer("hczk-platform")
    .issuedAt(new Date())
    .expiration(new Date(System.currentTimeMillis() + 3600000))
    .claim("merchant_id", merchantId)
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
    clockTolerance: 30     // 允许 30 秒时钟偏差
});
```

**Python 验证（智能体端，PyJWT）**

```python
import jwt
payload = jwt.decode(token, SHARED_SECRET, algorithms=['HS256'],
                     issuer='hczk-platform', leeway=30)
```

## 4. 接口详细规范

### 4.1 健康检测接口（必填）

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
    "database": {
      "status": "connected",
      "latency_ms": 5
    },
    "knowledge": {
      "status": "connected",
      "collection": "products",
      "document_count": 1234
    },
    "llm": {
      "status": "available",
      "model": "deepseek-chat"
    }
  },
  "runtime": {
    "node_version": "v20.11.0",
    "memory_usage_mb": 256,
    "active_sessions": 12
  }
}
```

**字段说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `status` | string | **必填** | 服务状态：`ok`=正常，`degraded`=降级运行，其他值=异常 |
| `version` | string | 可选 | 智能体版本号 |
| `uptime` | number | 可选 | 服务运行时长（秒） |
| `timestamp` | string | 可选 | 当前服务器时间（ISO 8601） |
| `components` | object | 可选 | 组件状态详情，结构自定义 |
| `components.database` | object | 可选 | 数据库连接状态 |
| `components.knowledge` | object | 可选 | 知识库连接状态 |
| `components.llm` | object | 可选 | LLM 可用性状态 |
| `runtime` | object | 可选 | 运行时信息，结构自定义 |

**状态判定规则**

| status 值 | 平台显示 | 含义 |
|-----------|---------|------|
| `ok` | 在线（绿色） | 服务完全正常 |
| `degraded` | 降级（黄色） | 部分功能受限但仍可工作 |
| 其他值 / 请求超时 | 离线（红色） | 服务不可用 |

**超时要求**：接口应在 **3 秒内** 返回响应，否则平台判定为离线。

---

### 4.2 对话接口（必填）

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
  "merchant_id": "M001",
  "collection_name": "products",
  "context": [
    {"role": "user", "content": "之前的对话内容"},
    {"role": "assistant", "content": "之前的回复内容"}
  ]
}
```

**请求字段说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `message` | string | **必填** | 用户消息内容 |
| `session_id` | string | **必填** | 会话 ID，同一会话保持一致 |
| `merchant_id` | string | **必填** | 商家 ID，用于多租户隔离 |
| `collection_name` | string | 可选 | 知识库集合名称 |
| `context` | array | 可选 | 历史对话上下文 |
| 其他自定义字段 | any | 可选 | 平台会透传请求中的所有字段 |

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

### 4.3 流式对话接口（可选，P0 优先级）

**请求**

```
POST {stream_endpoint}
Content-Type: application/json
Authorization: Bearer {jwt_token}
```

**请求体**：与 `POST /api/chat` 完全一致。

**响应**：`Content-Type: text/event-stream`

每行格式：`data: {JSON}\n\n`

> **重要**：每个 SSE 事件必须以 `\n\n`（两个换行符）结尾，否则多事件会被拼接在一起导致客户端解析异常。平台代理层会剥离 `data:` 前缀后重新包装为 SseEmitter 格式，智能体端无需特殊处理，但必须确保每行以 `\n\n` 结尾。

```
data: {"type":"thinking","content":"意图：order_query","metadata":{"confidence":0.95}}

data: {"type":"tool","content":"工具调用：query_order","metadata":{"tool_name":"query_order"}}

data: {"type":"content","content":"您好"}

data: {"type":"content","content":"！您的订单"}

data: {"type":"content","content":"已发货。"}

data: {"type":"done"}
```

**事件类型**

| type | 说明 | content | metadata |
|------|------|---------|----------|
| `thinking` | 思考/意图识别过程 | 思考内容摘要 | `confidence` 等 |
| `retrieval` | 知识库检索 | 检索结果摘要 | `strategy`, `count` 等 |
| `tool` | 工具调用 | 调用描述 | `tool_name`, `success` 等 |
| `content` | 回复内容片段 | 文本片段 | — |
| `done` | 结束 | — | — |

**降级策略**：如果智能体未配置 `stream_endpoint`，平台自动降级为同步调用 `chat_endpoint`，将完整回复包装为 SSE 格式返回。

---

### 4.4 智能体元信息接口（可选，P0 优先级）

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

### 4.5 会话历史接口（可选，P1 优先级）

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

### 4.6 清除会话接口（可选，P2 优先级）

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

### 4.7 文档上传接口（可选，P1 优先级）

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
| `merchant_id` | string | 可选 | 商家 ID |
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

## 5. 注册流程

### 5.1 管理员注册智能体

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

> **注意**：业务接口（对话、流式对话等）的鉴权已统一使用 JWT Token，不再依赖注册时配置的 `auth_header`。`auth_header` 仅用于兼容旧版智能体的健康检测和元信息接口。新注册的智能体无需配置此项。

3. 点击「注册」，平台立即调用健康检测接口验证可达性
4. 注册成功后，智能体卡片显示在管理界面，能力标签和工具列表通过元信息接口自动获取

### 5.2 绑定商家

在「用户智能体绑定」页面，将商家绑定到已注册的智能体，平台会将该商家的对话请求路由到此智能体。

### 5.3 配置共享密钥

智能体端需要配置与平台相同的 JWT 签名密钥，用于验证平台签发的 JWT Token：

- **开发环境**：与平台使用相同的密钥字符串
- **生产环境**：通过环境变量 `JWT_AGENT_SHARED_SECRET` 注入，与平台保持一致

---

## 6. 平台行为说明

### 6.1 健康监测

- 平台每 **30 秒** 自动调用所有智能体的健康检测接口
- 管理员可手动点击「检测」按钮触发单次检测
- 检测超时时间为 **3 秒**
- 检测结果实时反映在智能体卡片的状态指示灯上

### 6.2 对话路由

- 用户通过 API Key 发起对话请求
- 平台查询商家绑定 → 获取智能体 → 生成 JWT Token → 调用智能体的 `chat_endpoint`
- 平台向智能体发送的请求体包含 `merchant_id`、`message`、`session_id` 等字段
- JWT Token 中包含 `merchant_id` 和 `apiKey`，智能体端从 JWT 中提取使用
- 路由优先级：`agent_id`（平台智能体）> `agent_endpoint`（旧 Endpoint 模式）> `skill_id`（旧 Skill 模式）

### 6.3 流式对话

- 请求 `POST /api/chat/stream` 时，平台优先使用智能体的 `stream_endpoint`
- 如果未配置 `stream_endpoint`，自动降级为同步调用 `chat_endpoint`，将完整回复包装为 SSE 格式
- SSE 事件类型：`thinking`、`retrieval`、`tool`、`content`、`done`

### 6.4 元信息获取

- 页面加载时，平台自动调用配置了 `info_endpoint` 的智能体获取元信息
- 元信息用于展示能力标签、工具列表、模型信息
- 如果获取失败，降级显示数据库中注册时的基本信息

### 6.5 JWT 鉴权

- 平台调用智能体所有业务接口时，自动生成包含商家身份信息的 JWT Token
- 生成时机：每次调用智能体前即时生成，不缓存复用
- Token 通过 `Authorization: Bearer {jwt}` 请求头传递
- Token 有效期默认 1 小时（`jwt.agent.expiration`），每次调用生成新 Token
- 智能体端使用共享密钥验证 Token，从中提取 `merchant_id` 和 `apiKey`
- 试用场景下 `merchant_id` 为 `"trial"`，`apiKey` 为空
- 健康检测和元信息接口不使用 JWT，兼容旧版 `auth_header` 方式

---

## 7. 接入示例

### 7.1 Node.js (Express) 示例

```javascript
const express = require('express');
const jwt = require('jsonwebtoken');
const app = express();
app.use(express.json());

// 与平台共享的 JWT 签名密钥
const SHARED_SECRET = process.env.JWT_AGENT_SHARED_SECRET || 'hczk-ai-platform-secret-key-2024-very-long-and-secure';

// JWT 验证中间件
function verifyPlatformJWT(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: '缺少认证信息' });
  }

  try {
    const token = authHeader.substring(7);
    const payload = jwt.verify(token, SHARED_SECRET, { issuer: 'hczk-platform' });
    // 将解析后的身份信息挂载到请求对象
    req.merchant = {
      id: payload.merchant_id,
      apiKey: payload.apiKey || null,
      isTrial: payload.merchant_id === 'trial'
    };
    next();
  } catch (err) {
    return res.status(401).json({ error: 'JWT 验证失败: ' + err.message });
  }
}

// 健康检测接口（必填，无需认证）
app.get('/api/health', (req, res) => {
  res.json({
    status: 'ok',
    version: '1.0.0',
    uptime: process.uptime(),
    timestamp: new Date().toISOString(),
    components: {
      database: { status: 'connected', latency_ms: 3 },
      knowledge: { status: 'connected', collection: 'products', document_count: 500 },
      llm: { status: 'available', model: 'deepseek-chat' }
    }
  });
});

// 对话接口（必填，需 JWT 认证）
app.post('/api/chat', verifyPlatformJWT, async (req, res) => {
  const { message, session_id, merchant_id, collection_name } = req.body;
  const { apiKey, isTrial } = req.merchant;

  // 试用场景限制
  if (isTrial) {
    // 可返回示例数据或限制功能
  }

  try {
    // apiKey 可用于调用平台知识库/模型服务
    const reply = await processMessage(message, session_id, merchant_id, apiKey);
    res.json({
      reply,
      session_id,
      metadata: {
        input_tokens: 100,
        output_tokens: 200,
        model: 'deepseek-chat',
        thinking_content: '用户询问产品信息，检索知识库...',
        retrieval_used: true,
        fallback_used: false
      }
    });
  } catch (err) {
    res.status(500).json({
      error: err.message,
      code: 'PROCESSING_ERROR',
      session_id
    });
  }
});

// 流式对话接口（可选，需 JWT 认证）
app.post('/api/chat/stream', verifyPlatformJWT, async (req, res) => {
  const { message, session_id, merchant_id } = req.body;

  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');

  res.write(`data: ${JSON.stringify({ type: 'thinking', content: '意图识别中...' })}\n\n`);
  res.write(`data: ${JSON.stringify({ type: 'tool', content: '检索知识库', metadata: { tool_name: 'retrieval' } })}\n\n`);

  const chunks = ['您好', '！根据', '您的需求', '...'];
  for (const chunk of chunks) {
    res.write(`data: ${JSON.stringify({ type: 'content', content: chunk })}\n\n`);
    await new Promise(r => setTimeout(r, 100));
  }

  res.write(`data: ${JSON.stringify({ type: 'done' })}\n\n`);
  res.end();
});

// 智能体元信息接口（可选，无需认证）
app.get('/api/agent/info', (req, res) => {
  res.json({
    name: '客服智能体',
    version: '1.0.0',
    description: '支持订单查询、商品推荐、工单创建的客服智能体',
    capabilities: {
      knowledge_retrieval: true,
      tool_calling: true,
      multi_turn: true,
      streaming: true,
      thinking: true
    },
    model: 'deepseek-chat',
    tools: [
      { name: 'query_order', description: '查询订单状态' },
      { name: 'search_product', description: '搜索商品信息' },
      { name: 'create_ticket', description: '创建工单' }
    ]
  });
});

// 会话历史接口（可选，需 JWT 认证）
app.get('/api/chat/history/:sessionId', verifyPlatformJWT, async (req, res) => {
  const { sessionId } = req.params;
  const messages = await getHistory(sessionId, req.merchant.id);
  res.json({ session_id: sessionId, messages, message_count: messages.length });
});

// 清除会话接口（可选，需 JWT 认证）
app.delete('/api/chat/sessions/:sessionId', verifyPlatformJWT, async (req, res) => {
  const { sessionId } = req.params;
  await clearSession(sessionId, req.merchant.id);
  res.json({ status: 'ok', session_id: sessionId });
});

// 文档上传接口（可选，需 JWT 认证）
const multer = require('multer');
const upload = multer({ dest: '/tmp/uploads/' });

app.post('/api/documents', verifyPlatformJWT, upload.single('file'), async (req, res) => {
  const { collection_name, merchant_id } = req.body;
  const file = req.file;

  try {
    const result = await ingestDocument(file, collection_name, merchant_id, req.merchant.apiKey);
    res.json({
      status: 'ok',
      document_id: result.id,
      collection_name,
      chunk_count: result.chunks,
      message: '文档已成功入库'
    });
  } catch (err) {
    res.status(500).json({
      status: 'error',
      message: err.message,
      code: 'INGEST_ERROR'
    });
  }
});

app.listen(3000, () => console.log('Agent running on port 3000'));
```

### 7.2 Python (FastAPI) 示例

```python
from fastapi import FastAPI, UploadFile, File, Form, Depends, HTTPException, Request
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
import jwt
import time
import json
import asyncio

app = FastAPI()

# 与平台共享的 JWT 签名密钥
SHARED_SECRET = os.environ.get("JWT_AGENT_SHARED_SECRET", "hczk-ai-platform-secret-key-2024-very-long-and-secure")

class ChatRequest(BaseModel):
    message: str
    session_id: str
    merchant_id: str
    collection_name: str | None = None
    context: list | None = None

# JWT 验证依赖
def verify_platform_jwt(request: Request):
    auth_header = request.headers.get("Authorization", "")
    if not auth_header.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="缺少认证信息")

    token = auth_header[7:]
    try:
        payload = jwt.decode(token, SHARED_SECRET, algorithms=["HS256"], issuer="hczk-platform")
        return {
            "merchant_id": payload["merchant_id"],
            "api_key": payload.get("apiKey"),
            "is_trial": payload["merchant_id"] == "trial"
        }
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="JWT 已过期")
    except jwt.InvalidTokenError as e:
        raise HTTPException(status_code=401, detail=f"JWT 验证失败: {e}")

@app.get("/api/health")
async def health():
    return {
        "status": "ok",
        "version": "1.0.0",
        "uptime": int(time.time() - start_time),
        "components": {
            "database": {"status": "connected"},
            "knowledge": {"status": "connected"},
            "llm": {"status": "available", "model": "deepseek-chat"}
        }
    }

@app.post("/api/chat")
async def chat(req: ChatRequest, merchant=Depends(verify_platform_jwt)):
    # merchant["merchant_id"] 做多租户隔离
    # merchant["api_key"] 可用于调用平台知识库/模型服务
    reply = await process_message(req.message, req.session_id, req.merchant_id, merchant["api_key"])
    return {
        "reply": reply,
        "session_id": req.session_id,
        "metadata": {
            "input_tokens": 100,
            "output_tokens": 200,
            "thinking_content": "处理用户请求...",
            "retrieval_used": True,
            "fallback_used": False
        }
    }

@app.post("/api/chat/stream")
async def chat_stream(req: ChatRequest, merchant=Depends(verify_platform_jwt)):
    async def generate():
        yield f"data: {json.dumps({'type': 'thinking', 'content': '意图识别中...'})}\n\n"
        yield f"data: {json.dumps({'type': 'tool', 'content': '检索知识库', 'metadata': {'tool_name': 'retrieval'}})}\n\n"
        for chunk in ["您好", "！根据", "您的需求", "..."]:
            yield f"data: {json.dumps({'type': 'content', 'content': chunk})}\n\n"
            await asyncio.sleep(0.1)
        yield f"data: {json.dumps({'type': 'done'})}\n\n"

    return StreamingResponse(generate(), media_type="text/event-stream")

@app.get("/api/agent/info")
async def agent_info():
    return {
        "name": "客服智能体",
        "version": "1.0.0",
        "capabilities": {
            "knowledge_retrieval": True,
            "tool_calling": True,
            "multi_turn": True,
            "streaming": True,
            "thinking": True
        },
        "model": "deepseek-chat",
        "tools": [
            {"name": "query_order", "description": "查询订单状态"},
            {"name": "search_product", "description": "搜索商品信息"}
        ]
    }

@app.get("/api/chat/history/{session_id}")
async def get_history(session_id: str, merchant=Depends(verify_platform_jwt)):
    messages = await load_history(session_id, merchant["merchant_id"])
    return {"session_id": session_id, "messages": messages, "message_count": len(messages)}

@app.delete("/api/chat/sessions/{session_id}")
async def clear_session(session_id: str, merchant=Depends(verify_platform_jwt)):
    await delete_session(session_id, merchant["merchant_id"])
    return {"status": "ok", "session_id": session_id}

@app.post("/api/documents")
async def upload_document(
    file: UploadFile = File(...),
    collection_name: str = Form(...),
    merchant_id: str = Form(None),
    merchant=Depends(verify_platform_jwt)
):
    result = await ingest(file, collection_name, merchant_id, merchant["api_key"])
    return {
        "status": "ok",
        "document_id": result["id"],
        "collection_name": collection_name,
        "chunk_count": result["chunks"]
    }
```

---

## 8. 兼容性说明

- 所有可选接口不影响必填接口的正常工作
- 平台实现了完整的降级策略：
  - 不支持 `stream_endpoint` → 降级为同步调用 `chat_endpoint`
  - 不支持 `info_endpoint` → 显示数据库中注册时的基本信息
  - 不支持 `history_endpoint` → 会话历史和清除会话功能不可用
  - 不支持 `document_endpoint` → 文档上传功能不可用
- 新增接口可随时补充配置，无需重新注册智能体
- **JWT 鉴权**为强制要求，所有业务接口（对话、流式对话、会话历史、清除会话、文档上传）均需验证 JWT Token
- **健康检测和元信息**接口仍兼容旧版 `auth_header` 认证方式（如已配置）
- **无法接入 JWT 的旧版智能体**可使用自定义 `agent_endpoint` 和 `auth_header` 方式绑定，平台作为旧版端点转发
- SSE 流式数据：智能体端必须确保每行以 `\n\n` 结尾，平台代理层会自动处理 `data:` 前缀的剥离和重新包装

## 9. 常见问题

**Q: 健康检测接口响应慢会影响什么？**
A: 平台设置 3 秒超时，超时即判定为离线。建议健康检测接口只做轻量级检查，不要执行耗时操作。

**Q: 流式对话和同步对话的请求格式一样吗？**
A: 完全一样。区别仅在响应格式：同步返回完整 JSON，流式返回 SSE 事件流。

**Q: 元信息接口返回的数据会缓存吗？**
A: 平台每次加载智能体管理页面时都会重新获取元信息，不做长期缓存。

**Q: 会话历史接口的路径规则是什么？**
A: 注册时填写基础路径（如 `http://host:3000/api/chat/history`），平台自动拼接 `/{session_id}`。GET 方法获取历史，DELETE 方法清除会话。

**Q: 多个智能体可以共用同一个运行时吗？**
A: 可以。每个智能体注册时填写各自的接口地址即可，平台按接口地址独立检测和路由。

**Q: JWT Token 过期了怎么办？**
A: 平台每次调用智能体时都会生成新的 JWT Token，有效期1小时。智能体端只需验证 Token 有效性，无需处理刷新逻辑。

**Q: 智能体端如何获取共享密钥？**
A: 开发环境可直接使用与平台相同的密钥字符串；生产环境通过环境变量 `JWT_AGENT_SHARED_SECRET` 注入，与平台保持一致。

**Q: 试用场景和正式场景的 JWT 有什么区别？**
A: 试用场景下 `merchant_id` 为 `"trial"`，`apiKey` 为空；正式场景下 `merchant_id` 为实际商家 ID，`apiKey` 为绑定的平台 API Key。智能体端可通过 `merchant_id === "trial"` 区分。

**Q: 智能体端验证 JWT 失败应该返回什么？**
A: 返回 HTTP 401 状态码，响应体包含错误信息：`{"error": "JWT 验证失败: 具体原因"}`。平台会根据响应状态码进行相应处理。

**Q: 注册时还需要填写认证头吗？**
A: 新注册的智能体不需要。业务接口已统一使用 JWT 鉴权。`auth_header` 仅用于兼容旧版智能体的健康检测和元信息接口。如果旧版智能体需要自定义认证，仍可填写。

**Q: 平台代理 SSE 时对数据有什么要求？**
A: 智能体端和普通 SSE 服务端一样，输出 `data: {JSON}\n\n` 格式即可。平台代理层会自动剥离 `data:` 前缀，通过 Spring SseEmitter 重新包装后返回给前端。关键是每个事件要以 `\n\n` 结尾，否则多事件会粘连到一起。

**Q: JWT Token 中的 apiKey 字段什么时候为空？**
A: 两种场景：(1) 试用智能体时（`merchant_id` 为 `"trial"`）；(2) 旧版通用运行时转发时。智能体端应兼容 `apiKey` 为空的情况。
