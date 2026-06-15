# 桓宸智科 AI 平台 — 智能体接入规范

> 版本：1.1.0
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

## 3. 接口详细规范

### 3.1 健康检测接口（必填）

**请求**

```
GET {health_endpoint}
Authorization: {auth_header}  （如果注册时配置了认证头）
```

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

### 3.2 对话接口（必填）

**请求**

```
POST {chat_endpoint}
Content-Type: application/json
Authorization: {auth_header}  （如果注册时配置了认证头）
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

### 3.3 流式对话接口（可选，P0 优先级）

**请求**

```
POST {stream_endpoint}
Content-Type: application/json
Authorization: {auth_header}
```

**请求体**：与 `POST /api/chat` 完全一致。

**响应**：`Content-Type: text/event-stream`

每行格式：`data: {JSON}\n\n`

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

### 3.4 智能体元信息接口（可选，P0 优先级）

**请求**

```
GET {info_endpoint}
Authorization: {auth_header}
```

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

### 3.5 会话历史接口（可选，P1 优先级）

**请求**

```
GET {history_endpoint}/{session_id}
Authorization: {auth_header}
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

### 3.6 清除会话接口（可选，P2 优先级）

**请求**

```
DELETE {history_endpoint}/{session_id}
Authorization: {auth_header}
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

### 3.7 文档上传接口（可选，P1 优先级）

**请求**

```
POST {document_endpoint}
Content-Type: multipart/form-data
Authorization: {auth_header}
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

## 4. 注册流程

### 4.1 管理员注册智能体

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

**认证配置**

| 字段 | 必填 | 说明 |
|------|------|------|
| 认证头 | 否 | 如 `Bearer sk-xxx`，平台调用时携带 |

3. 点击「注册」，平台立即调用健康检测接口验证可达性
4. 注册成功后，智能体卡片显示在管理界面，能力标签和工具列表通过元信息接口自动获取

### 4.2 绑定商家

在「用户智能体绑定」页面，将商家绑定到已注册的智能体，平台会将该商家的对话请求路由到此智能体。

---

## 5. 平台行为说明

### 5.1 健康监测

- 平台每 **30 秒** 自动调用所有智能体的健康检测接口
- 管理员可手动点击「检测」按钮触发单次检测
- 检测超时时间为 **3 秒**
- 检测结果实时反映在智能体卡片的状态指示灯上

### 5.2 对话路由

- 用户通过 API Key 发起对话请求
- 平台查询商家绑定 → 获取智能体 → 调用智能体的 `chat_endpoint`
- 平台透传请求中的所有字段，智能体可自行扩展
- 路由优先级：`agent_id`（平台智能体）> `agent_endpoint`（旧 Endpoint 模式）> `skill_id`（旧 Skill 模式）

### 5.3 流式对话

- 请求 `POST /api/chat/stream` 时，平台优先使用智能体的 `stream_endpoint`
- 如果未配置 `stream_endpoint`，自动降级为同步调用 `chat_endpoint`，将完整回复包装为 SSE 格式
- SSE 事件类型：`thinking`、`retrieval`、`tool`、`content`、`done`

### 5.4 元信息获取

- 页面加载时，平台自动调用配置了 `info_endpoint` 的智能体获取元信息
- 元信息用于展示能力标签、工具列表、模型信息
- 如果获取失败，降级显示数据库中注册时的基本信息

### 5.5 认证

- 如果注册时配置了 `auth_header`，平台调用智能体所有接口时都会携带此认证头
- 格式：`Authorization: {auth_header}`

---

## 6. 接入示例

### 6.1 Node.js (Express) 示例

```javascript
const express = require('express');
const app = express();
app.use(express.json());

// 健康检测接口（必填）
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

// 对话接口（必填）
app.post('/api/chat', async (req, res) => {
  const { message, session_id, merchant_id, collection_name } = req.body;

  try {
    const reply = await processMessage(message, session_id, merchant_id);
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

// 流式对话接口（可选）
app.post('/api/chat/stream', async (req, res) => {
  const { message, session_id, merchant_id } = req.body;

  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');

  // 发送思考过程
  res.write(`data: ${JSON.stringify({ type: 'thinking', content: '意图识别中...' })}\n\n`);

  // 发送工具调用
  res.write(`data: ${JSON.stringify({ type: 'tool', content: '检索知识库', metadata: { tool_name: 'retrieval' } })}\n\n`);

  // 流式发送内容
  const chunks = ['您好', '！根据', '您的需求', '...'];
  for (const chunk of chunks) {
    res.write(`data: ${JSON.stringify({ type: 'content', content: chunk })}\n\n`);
    await new Promise(r => setTimeout(r, 100));
  }

  // 结束
  res.write(`data: ${JSON.stringify({ type: 'done' })}\n\n`);
  res.end();
});

// 智能体元信息接口（可选）
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

// 会话历史接口（可选）
app.get('/api/chat/history/:sessionId', async (req, res) => {
  const { sessionId } = req.params;
  const messages = await getHistory(sessionId);
  res.json({ session_id: sessionId, messages, message_count: messages.length });
});

// 清除会话接口（可选）
app.delete('/api/chat/sessions/:sessionId', async (req, res) => {
  const { sessionId } = req.params;
  await clearSession(sessionId);
  res.json({ status: 'ok', session_id: sessionId });
});

// 文档上传接口（可选）
const multer = require('multer');
const upload = multer({ dest: '/tmp/uploads/' });

app.post('/api/documents', upload.single('file'), async (req, res) => {
  const { collection_name, merchant_id } = req.body;
  const file = req.file;

  try {
    const result = await ingestDocument(file, collection_name, merchant_id);
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

### 6.2 Python (FastAPI) 示例

```python
from fastapi import FastAPI, UploadFile, File, Form
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
import time
import json

app = FastAPI()

class ChatRequest(BaseModel):
    message: str
    session_id: str
    merchant_id: str
    collection_name: str | None = None
    context: list | None = None

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
async def chat(req: ChatRequest):
    reply = await process_message(req.message, req.session_id, req.merchant_id)
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
async def chat_stream(req: ChatRequest):
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
async def get_history(session_id: str):
    messages = await load_history(session_id)
    return {"session_id": session_id, "messages": messages, "message_count": len(messages)}

@app.delete("/api/chat/sessions/{session_id}")
async def clear_session(session_id: str):
    await delete_session(session_id)
    return {"status": "ok", "session_id": session_id}

@app.post("/api/documents")
async def upload_document(
    file: UploadFile = File(...),
    collection_name: str = Form(...),
    merchant_id: str = Form(None)
):
    result = await ingest(file, collection_name, merchant_id)
    return {
        "status": "ok",
        "document_id": result["id"],
        "collection_name": collection_name,
        "chunk_count": result["chunks"]
    }
```

---

## 7. 兼容性说明

- 所有可选接口不影响必填接口的正常工作
- 平台实现了完整的降级策略：
  - 不支持 `stream_endpoint` → 降级为同步调用 `chat_endpoint`
  - 不支持 `info_endpoint` → 显示数据库中注册时的基本信息
  - 不支持 `history_endpoint` → 会话历史和清除会话功能不可用
  - 不支持 `document_endpoint` → 文档上传功能不可用
- 新增接口可随时补充配置，无需重新注册智能体

## 8. 常见问题

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
