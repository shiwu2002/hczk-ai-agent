# 桓宸智科 AI 平台 — 开发文档

> 版本：7.0.0
> 日期：2026-06-22

---

## 一、项目概述

桓宸智科 AI 平台是一个面向企业与个人的 AI 智能体管理与计费系统。平台采用**智能体注册中心**架构，所有智能体作为独立服务部署在容器中，通过标准接口注册到平台，平台统一负责健康监测、对话路由、计费扣款。

### 核心功能

- **管理员端**：智能体注册与管理、模型接入管理、API Key 分配、用户管理、Token 计费管理、平台对接配置
- **用户端**：智能体使用、用量统计、API Key 管理、充值中心、账单查询
- **平台对接**：美团智能客服、抖音智能客服 Webhook 接入

---

## 二、技术架构

### 2.1 技术栈

| 层级 | 技术选型 |
|------|----------|
| 前端 | Vue 3 + Vite + Tailwind CSS v4 + Pinia + Vue Router |
| 后端 | Spring Boot 3.2.5 + Java 21 |
| 数据库 | MySQL 8.0 |
| 向量数据库 | Milvus（知识库服务） |
| 缓存 | Redis 7.x |
| 安全 | Spring Security + JWT |
| 构建工具 | Maven 3.9+ / npm |

### 2.2 系统架构图

```
+-------------------+      +-------------------+      +-------------------+
|   Vue 3 前端      |<---->|  Spring Boot 后端 |<---->|   MySQL 数据库    |
|  (hczk-agent-web) | REST | (hczk-ai-agent-   | MyBatis|                 |
|                   | API  |      server)       | -Plus  +-------------------+
+-------------------+      +-------------------+      +-------------------+
                                    |                    Redis 缓存       |
                                    +----------------->+-------------------+
                                    |
                           +--------+--------+      +-------------------+
                           |  注册的容器智能体  |      |  Milvus 向量数据库 |
                           |  (各智能体服务)    |      |  (知识库服务)     |
                           |  health/chat/...  |      +-------------------+
                           +------------------+
```

### 2.3 智能体注册中心架构

每个智能体作为独立服务部署，通过实现标准接口注册到平台：

```
平台管理后台 → 注册智能体（填写 healthEndpoint / chatEndpoint 等）
    ↓
平台定期调用 healthEndpoint 检测状态
    ↓
用户对话请求 → 平台查商家绑定 → 路由到智能体 chatEndpoint
```

---

## 三、数据库设计

### 3.1 核心表

| 表名 | 说明 |
|------|------|
| users | 用户表（含雪花 userId） |
| ai_models | AI 模型表（平台接入的 LLM 模型配置） |
| agents | 智能体表（注册的容器智能体） |
| api_keys | API 密钥表（含 unit_price 计费单价） |
| skill | Skill 工具组表（含 public/private 可见性） |
| tool_definition | 工具定义表（function calling schema，type=builtin/api） |
| user_skill_binding | 用户-Skill 绑定表（private 工具组授权） |
| cli_tool_registry | CLI-Anything 工具注册表（从 GitHub 同步，含 is_enabled 市场开关） |
| cli_tool_command | CLI 工具命令表（从 SKILL.md 解析的命令列表） |
| merchant_agent_binding | 商家智能体绑定表 |
| retrieval_logs | 检索日志表（监控命中率、耗时、策略） |
| billing_records | 计费记录表 |
| recharge_records | 充值记录表 |

### 3.2 agents 表结构（当前）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| name | VARCHAR(100) | 智能体名称 |
| description | TEXT | 描述 |
| agent_type | VARCHAR(50) | 类型标识（自定义，如 customer_service） |
| health_endpoint | VARCHAR(256) | 健康检测接口地址（必填） |
| chat_endpoint | VARCHAR(256) | 对话接口地址（必填） |
| stream_endpoint | VARCHAR(256) | 流式对话接口地址（可选） |
| document_endpoint | VARCHAR(256) | 文档上传接口地址（可选） |
| info_endpoint | VARCHAR(256) | 智能体元信息接口地址（可选） |
| history_endpoint | VARCHAR(256) | 会话历史接口地址（可选） |
| auth_header | VARCHAR(256) | 调用接口时的认证头 |
| version | VARCHAR(32) | 智能体服务版本号 |
| user_id | BIGINT FK | 所属用户 |
| status | TINYINT | 0=启用，1=禁用 |
| total_calls | BIGINT | 总调用次数 |
| total_tokens | BIGINT | 总 Token 数 |

> 完整建表 SQL 见 `hczk-ai-agent-server/doce/init.sql`

### 3.3 merchant_agent_binding 表结构

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| merchant_id | VARCHAR(64) | 商家 ID |
| agent_id | BIGINT FK | 绑定的平台注册智能体（新架构） |
| skill_id | VARCHAR(64) | 绑定的 Skill（兼容旧模式） |
| agent_endpoint | VARCHAR(256) | 定制智能体地址（兼容旧模式） |
| agent_auth_header | VARCHAR(256) | 认证头（兼容旧模式） |
| enabled | BOOLEAN | 是否启用 |

---

## 四、API 接口文档

### 4.1 认证相关

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/auth/login | POST | 登录 |
| /api/auth/register | POST | 注册 |

### 4.2 用户管理

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/users/me | GET | 获取当前用户信息 |
| /api/users | GET | 获取所有用户（管理员） |
| /api/users/{id} | PUT | 更新用户信息 |
| /api/users/{id}/password | PUT | 修改用户密码（需校验旧密码） |

### 4.3 模型管理

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/models | GET | 获取所有模型 |
| /api/models | POST | 创建模型 |
| /api/models/{id} | PUT | 更新模型 |
| /api/models/{id} | DELETE | 删除模型 |
| /api/models/{id}/toggle | POST | 切换模型状态 |

### 4.4 智能体管理（注册中心）

> **路径变更说明**：智能体 CRUD 已从 `/platform/agents` 迁移至 `/agents`，由 AgentController 统一提供。
> `/platform/**` 路径现在限制为 ADMIN 角色才能访问。

| 接口 | 方法 | 说明 |
|------|------|------|
| /agents | GET | 获取智能体列表（ADMIN 获取全部，普通用户仅自己的） |
| /agents | POST | 注册智能体 |
| /agents/{id} | GET | 获取智能体详情 |
| /agents/{id} | PUT | 更新智能体 |
| /agents/{id} | DELETE | 注销智能体 |
| /agents/{id}/toggle | POST | 切换启用/禁用 |
| /agents/health | GET | 批量健康检测（公开） |
| /agents/{id}/health | GET | 单个健康检测（公开） |
| /agents/{id}/info | GET | 获取智能体元信息 |

### 4.5 API Key 管理

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/api-keys | GET | 获取所有 API Key |
| /api/api-keys | POST | 创建 API Key |
| /api/api-keys/{id} | PUT | 更新 API Key |
| /api/api-keys/{id} | DELETE | 删除 API Key |

### 4.6 聊天接口

| 接口 | 方法 | 认证方式 | 说明 |
|------|------|----------|------|
| /chat/completions | POST | JWT | 内部调用 |
| /v1/chat/completions | POST | API Key | OpenAI 兼容接口 |
| /api/chat | POST | API Key | 平台路由（同步） |
| /api/chat/stream | POST | API Key | 平台路由（流式 SSE） |
| /api/chat/history/{sessionId} | GET | API Key | 获取会话历史 |
| /api/chat/sessions/{sessionId} | DELETE | API Key | 清除会话 |

### 4.7 计费管理

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/billing/records/{userId} | GET | 用户账单记录 |
| /api/billing/recharge | POST | 充值 |
| /api/billing/balance/{userId} | GET | 查询余额 |

### 4.8 平台对接

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/platforms | GET | 获取所有平台配置 |
| /api/platforms | POST | 保存平台配置 |
| /api/webhook/meituan | POST | 美团消息回调 |
| /api/webhook/douyin | POST | 抖音消息回调 |

### 4.9 CLI-Anything 工具市场

CLI-Anything 是一个命令行工具注册表，平台从 GitHub 同步 CLI 工具的元数据（registry.json + SKILL.md），管理员在市场启用后，智能体通过 Skill 发现和安装。

**架构（v3）**：平台只管元数据，智能体自行 pip install 并在本地执行。

**核心接口**：

| 接口 | 方法 | 说明 |
|------|------|------|
| /platform/cli-anything/clis | GET | 管理员查看所有 CLI 工具（含启用状态） |
| /platform/cli-anything/clis/{name}/enable | POST | 管理员启用工具（is_enabled=true） |
| /platform/cli-anything/clis/{name}/disable | POST | 管理员禁用工具（is_enabled=false） |
| /platform/cli-anything/sync | POST | 管理员手动从 GitHub 同步注册表 |

**系统 Skill "CLI工具市场"（name=cli-market）**：

| 工具名 | 类型 | 说明 |
|--------|------|------|
| cli_tools_list | builtin | 返回已启用 + 已同步的 CLI 工具列表 |
| cli_tools_install | builtin | 返回指定 CLI 工具的完整安装元数据（install_cmd、entry_point、commands） |

**智能体使用流程**：
1. 收到"CLI工具市场" Skill → 调用 `cli_tools_list` 浏览可用工具
2. 选择工具 → 调用 `cli_tools_install("jumpserver")` 获取元数据
3. 本地执行 `pip install` → 通过 entry_point 直接执行 CLI 命令

**相关表**：`cli_tool_registry`（含 is_enabled 市场开关）、`cli_tool_command`

---

## 五、计费规则

### 5.1 Token 计费流程

```
用户发送消息（API Key 认证）
    ↓
ChatService / 路由到智能体
    ↓ 1. 通过 API Key 查询所属用户
    ↓ 2. 余额预检查：预估本次调用费用（输入Token + 预估输出Token×1.5），余额不足则拒绝请求
    ↓ 3. 估算输入 tokens
    ↓ 4. 调用模型/智能体，获取回复
    ↓
processBilling()
    ↓ 1. 估算输出 tokens
    ↓ 2. 费用 = (input_tokens + output_tokens) / 1000 × unit_price
    ↓ 3. BillingService.deductBalance() 扣减余额 + 写入 billing_records
    ↓ 4. 更新 API Key 统计
    ↓ 5. 扣费失败时记入欠费日志，下次请求时余额预检查拦截
```

### 5.2 Token 估算规则

| 文本类型 | 估算比率 |
|----------|----------|
| 综合混合文本 | 约 2 字符 = 1 token（默认） |

### 5.3 计费公式

```
费用 = (input_tokens + output_tokens) / 1000 × unit_price
```

- `unit_price`：统一 Token 单价（元/千 Tokens），存储在 `api_keys.unit_price`
- 费用精确到小数点后 6 位，四舍五入

### 5.4 余额不足处理

- 请求前进行余额预检查：预估本次调用费用（输入Token + 预估输出Token×1.5），余额不足则直接拒绝请求
- 流式响应完成后扣费，若扣费失败（余额为负），记入欠费日志
- 下次请求时余额预检查会拦截（余额为负或不足预估费用）
- 用户需充值后才能继续调用

---

## 六、部署指南

### 6.1 环境要求

- JDK 21+
- Node.js 18+
- Maven 3.9+
- MySQL 8.0+（生产环境）
- Redis 7.x（可选，生产推荐）

### 6.2 后端部署

```bash
cd hczk-ai-agent-server
mvn clean package -DskipTests
java -jar target/hczk-ai-agent-server-0.0.1-SNAPSHOT.jar
```

### 6.3 前端部署

```bash
cd hczk-agent-web
npm install
npm run build
# 部署 dist 目录到 Nginx/静态服务器
```

> **环境变量配置**：前端通过 `VITE_API_BASE` 环境变量指定后端 API 地址。
> - 开发环境：`.env` 文件中配置 `VITE_API_BASE=http://localhost:8080/api`
> - 生产环境：`.env.production` 文件中配置 `VITE_API_BASE=/api`（或实际 API 地址）
> - 也可通过构建参数覆盖：`VITE_API_BASE=https://api.example.com/api npm run build`

### 6.4 数据库初始化

```bash
mysql -u root -p hczk_ai_platform < doce/init.sql
```

如从旧版本升级，执行迁移脚本：

```bash
mysql -u root -p hczk_ai_platform < doce/migration_v2_to_v3.sql
```

---

## 七、默认账号

| 账号 | 密码 | 角色 |
|------|------|------|
| admin | admin123 | 管理员 |
| user | user123 | 普通用户 |

---

## 八、相关文档

| 文档 | 路径 | 说明 |
|------|------|------|
| 智能体接入规范 | `docs/hhf/agent-integration-spec.md` | 容器智能体注册到平台的接口规范 |
| 知识库 API | `docs/hhf/knowledge-api.md` | Java 版知识库服务接口文档 |
| 知识库功能全景 | `docs/hhf/knowledge-feature-spec.md` | 知识库服务完整功能规格 |
| 平台开发者接入指南 | `docs/hhf/platform-integration-guide.md` | 通用智能体运行时接入平台指南 |
| 数据库初始化 | `hczk-ai-agent-server/doce/init.sql` | 完整建表 SQL |
| v12→v13 CLI注册表 | `doce/migration_v12_to_v13_cli_anything.sql` | CLI-Anything 注册表本地存储 |
| v13→v14 CLI市场 | `doce/migration_v13_to_v14_cli_market.sql` | CLI 工具市场重构（is_enabled + 系统 Skill） |
