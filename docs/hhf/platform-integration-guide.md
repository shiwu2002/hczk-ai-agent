# 通用智能体运行时 — 平台开发者接入指南

> 版本：7.0.0
> 日期：2026-06-22
> 说明：本文档描述通用智能体运行时如何接入管理平台。当前平台已升级为"智能体注册中心"架构，通用运行时作为一个智能体注册到平台。

---

## 一、架构概述

### 1.1 核心设计思想

**平台做统一入口，所有智能体（包括通用运行时）都是平台背后的一个 endpoint。**

当前平台采用**智能体注册中心**架构：

| 模式 | 说明 | 适合场景 |
|------|------|---------|
| **注册智能体模式** | 智能体注册到平台，填写 health/chat/stream 等端点地址 | 所有智能体（包括通用运行时、定制智能体） |
| **Skill 模式（兼容）** | 商家绑定一个 Skill 配置包，走通用运行时执行 | 标准客服、销售、FAQ 等通用场景 |
| **Endpoint 模式（兼容）** | 商家绑定一个独立智能体地址，平台直接转发 | 完全定制化的智能体 |

> 新架构中，通用运行时和定制智能体都通过注册中心管理，Skill/Endpoint 模式为兼容旧绑定。

### 1.2 整体架构图

```
┌──────────────────────────────────────────────────────────────────────┐
│                          管理平台                                     │
│                                                                      │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐  ┌───────────────────┐  │
│  │ 智能体注册 │  │ Skill 管理│  │ 计费/监控  │  │ API 网关（统一路由）│  │
│  │   中心    │  │          │  │           │  │                   │  │
│  └──────────┘  └──────────┘  └───────────┘  └───────────────────┘  │
│                                                                      │
│  网关路由逻辑：查 merchant_agent_binding                              │
│    → agent_id 有值 → 转发到注册的智能体 chatEndpoint                  │
│    → agent_endpoint 有值 → 直接转发（兼容旧模式）                     │
│    → skill_id 有值 → 转发到通用运行时（兼容旧模式）                    │
└──────────┬───────────────────────┬───────────────────────────────────┘
           │                       │
           ▼                       ▼
┌──────────────────────┐  ┌──────────────────┐
│  通用智能体运行时      │  │  其他注册智能体    │
│  （已注册到平台）      │  │  （已注册到平台）  │
│                      │  │                  │
│  health: /api/health │  │  health: /api/... │
│  chat: /api/chat     │  │  chat: /api/...   │
│  stream: /api/chat/  │  │  stream: /api/... │
│         stream       │  │                  │
│  info: /api/agent/   │  │  info: /api/...   │
│       info           │  │                  │
│  history: /api/chat/ │  │  document: /api/..│
│          history     │  │                  │
│  document: /api/     │  │                  │
│           documents  │  │                  │
└──────────────────────┘  └──────────────────┘
```

### 1.3 职责划分

| 职责 | 平台 | 通用运行时 | 其他注册智能体 |
|------|:----:|:----------:|:-------------:|
| 智能体注册/管理 | ✅ | ❌ | ❌ |
| 健康监测 | ✅ | ✅ 实现 /api/health | ✅ 实现 /api/health |
| 用户请求路由 | ✅ | ❌ | ❌ |
| 计费扣款 | ✅ | ❌ | ❌ |
| 对话执行 | ❌ | ✅ | ✅ |
| 知识检索 | ❌ | ✅ | 自行实现 |
| 工具调用 | ❌ | ✅ | 自行实现 |

---

## 二、通用运行时接入平台

通用运行时需要实现以下接口才能注册到平台（详见 `agent-integration-spec.md`）：

### 2.1 必填接口

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 健康检测 | GET | `/api/health` | 平台定期调用检测状态 |
| 对话 | POST | `/api/chat` | 平台转发用户对话 |

### 2.2 可选接口

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 流式对话 | POST | `/api/chat/stream` | SSE 格式流式输出 |
| 智能体元信息 | GET | `/api/agent/info` | 返回能力、工具、模型信息 |
| 会话历史 | GET | `/api/chat/history/{session_id}` | 获取历史消息 |
| 清除会话 | DELETE | `/api/chat/sessions/{session_id}` | 清除会话 |
| 文档上传 | POST | `/api/documents` | 上传知识库文档 |

### 2.3 注册配置

在平台管理后台注册通用运行时时填写：

| 字段 | 值 |
|------|-----|
| 名称 | 通用智能体运行时 |
| 类型标识 | `general_runtime` |
| 健康检测接口 | `http://runtime:3000/api/health` |
| 对话接口 | `http://runtime:3000/api/chat` |
| 流式对话接口 | `http://runtime:3000/api/chat/stream` |
| 元信息接口 | `http://runtime:3000/api/agent/info` |
| 会话历史接口 | `http://runtime:3000/api/chat/history` |
| 文档上传接口 | `http://runtime:3000/api/documents` |

---

## 三、商家专属知识库区分机制

通用运行时通过 **collectionName** 和 **agentId** 区分每个商家的知识库：

### 3.1 collectionName 三层优先级

| 优先级 | 来源 | 场景 |
|--------|------|------|
| 1（最高） | 请求参数 `collection_name` | 临时指定知识库 |
| 2 | Skill `capabilities.collectionName` | 绑定时通过 `capabilities_override` 指定 |
| 3（兜底） | 默认值 `'default'` | 公共知识库 |

### 3.2 agentId 两层优先级

| 优先级 | 来源 | 场景 |
|--------|------|------|
| 1（最高） | 请求参数 `agent_id` | 每次调用指定智能体 ID |
| 2（兜底） | 配置文件 `knowledge.agentId` | 默认智能体 ID |

### 3.3 知识库集合命名规则

Milvus 集合命名：`kb_{agentId}_{collectionName}`

例如：`kb_agent_001_merchant_001_products`

---

## 四、运行时环境配置

```env
# LLM 服务
LLM_BASE_URL=http://127.0.0.1:1234/v1
LLM_API_KEY=
LLM_MODEL=qwen/qwen3.5-9b
LLM_SUMMARY_MODEL=qwen3.5-4b

# 知识库服务
KNOWLEDGE_REMOTE_URL=http://localhost:3001
KNOWLEDGE_API_KEY=
KNOWLEDGE_AGENT_ID=default_agent

# 服务端口
PORT=3000

# 日志级别
LOG_LEVEL=info

# 数据库路径
DB_PATH=./data/agent.db
```

---

## 五、Skill 配置包详解

### 5.1 完整结构

```json
{
  "skillId": "customer_service",
  "version": "1.0.0",
  "persona": {
    "name": "智能客服",
    "systemPrompt": "你是一个专业的客服助手...",
    "tone": "专业、友好",
    "instructions": ["不要编造信息"]
  },
  "capabilities": {
    "tools": ["query_order", "query_product", "ticket_operation"],
    "knowledgeRetrieval": true,
    "collectionName": "default",
    "maxToolIterations": 5
  },
  "workflow": {
    "entry": "intent_classifier",
    "nodes": [...],
    "edges": [...]
  },
  "config": {
    "maxIterations": 5,
    "toolTimeoutMs": 10000,
    "temperature": 0.7
  }
}
```

### 5.2 可用的节点类型

| NodeType | 说明 | 典型用途 |
|----------|------|---------|
| `intent_classifier` | 意图分类 | Workflow 入口节点 |
| `knowledge_retrieval` | 知识检索 | FAQ、文档查询 |
| `tool_call` | 工具调用 | 查订单、查商品 |
| `llm_chat` | LLM 对话 | 最终回复节点 |
| `summarize` | 知识总结 | 检索后压缩上下文 |
| `rerank` | 重排序 | 提升检索精度 |
| `condition` | 条件分支 | 置信度检查 |
| `human_transfer` | 转人工 | 用户要求转人工 |
| `parallel` | 并行执行 | 同时检索多个知识库 |
| `loop` | 循环执行 | 分页查询、重试 |
| `webhook` | 调用外部 HTTP API | 调用商家自有 API |
| `template_response` | 模板回复 | 欢迎语、固定提示 |
| `custom` | 自定义 | 商家专属逻辑 |

### 5.3 商家覆盖机制

绑定商家时可通过 override 覆盖 Skill 默认值：

```json
{
  "merchant_id": "merchant_001",
  "skill_id": "customer_service",
  "persona_override": { "name": "XX数码客服" },
  "capabilities_override": { "collectionName": "merchant_001_products" },
  "config_override": { "temperature": 0.4 }
}
```

合并逻辑：`最终配置 = Skill 默认值 + override（覆盖同名字段）`

---

## 六、运行时管理接口

通用运行时提供的平台管理接口（用于 Skill 模式）：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/platform/skills` | 注册/更新 Skill |
| GET | `/api/platform/skills` | 查看所有 Skill |
| DELETE | `/api/platform/skills/:skillId` | 删除 Skill |
| POST | `/api/platform/bindings` | 绑定商家 |
| GET | `/api/platform/bindings` | 查看所有绑定 |
| DELETE | `/api/platform/bindings/:merchantId` | 解绑 |
| POST | `/api/platform/cache/reload` | 刷新缓存 |

---

## 七、接入步骤

### 第一步：部署运行时

```bash
npm install
cp .env.example .env
# 编辑 .env，填入 LLM 和知识库服务地址
npm run dev
```

### 第二步：在平台注册通用运行时

在平台管理后台「智能体管理」页面注册，填写各端点地址。

### 第三步：创建 Skill 并绑定商家

```bash
# 注册客服型 Skill
curl -X POST http://localhost:3000/api/platform/skills \
  -H "Content-Type: application/json" \
  -d @skill_definition.json

# 绑定商家
curl -X POST http://localhost:3000/api/platform/bindings \
  -H "Content-Type: application/json" \
  -d '{"merchant_id":"merchant_001","skill_id":"customer_service","enabled":true}'
```

### 第四步：验证

```bash
# 健康检查
curl http://localhost:3000/api/health

# 对话测试
curl -X POST http://localhost:3000/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"你好","merchant_id":"merchant_001"}'
```
