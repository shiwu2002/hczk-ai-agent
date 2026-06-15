# 智能体管理界面重构计划

## 概述

将当前分散的智能体管理（Agents.vue）和通用智能体检测（Monitor.vue）统一合并到智能体管理界面，每个智能体以小卡片形式展示实时状态，删除独立的监控页面。

## 当前状态分析

### 现有架构问题
1. **智能体管理与监控分离**：Agents.vue 只做 CRUD，Monitor.vue 只做运行时健康检测，两者割裂
2. **Monitor.vue 检测的是外部通用运行时**（`http://localhost:3000/api/health`），而非每个智能体的独立状态
3. **智能体卡片缺少实时健康状态**：当前卡片只显示静态信息（名称、类型、调用次数），没有在线/离线状态
4. **导航冗余**：侧边栏同时有"智能体监控"和"智能体管理"两个入口

### 关键文件
| 文件 | 作用 |
|------|------|
| `hczk-agent-web/src/views/admin/Agents.vue` | 智能体管理页面 |
| `hczk-agent-web/src/views/admin/Monitor.vue` | 智能体监控页面（待删除） |
| `hczk-agent-web/src/views/admin/Layout.vue` | 管理后台侧边栏导航 |
| `hczk-agent-web/src/router/index.js` | 前端路由 |
| `hczk-ai-agent-server/.../controller/PlatformController.java` | 平台接口（含健康检测） |
| `hczk-ai-agent-server/.../controller/AgentController.java` | 智能体 CRUD 接口 |
| `hczk-ai-agent-server/.../entity/Agent.java` | 智能体实体 |
| `hczk-ai-agent-server/.../service/impl/AgentServiceImpl.java` | 智能体服务实现 |

---

## 变更方案

### 一、后端：新增智能体健康检测接口

**文件**: `hczk-ai-agent-server/src/main/java/com/hczk/hczkaiagentserver/controller/AgentController.java`

**新增接口**: `GET /agents/health`

功能：批量检测所有智能体的健康状态，返回每个智能体的在线/离线状态。

检测逻辑按智能体类型区分：
- **MODEL 类型**：通过 `ai_models` 表获取 `api_base` + `api_key`，发送轻量级请求（如 `GET /models`）检测模型 API 可达性
- **SKILL 类型**：调用通用运行时的健康端点 `http://localhost:3000/api/health`，检查运行时是否在线
- **ENDPOINT 类型**：对 `endpoint` 地址发送 HTTP HEAD/GET 请求检测可达性

返回格式：
```json
{
  "code": 200,
  "data": {
    "1": { "online": true, "latencyMs": 45, "message": "运行正常", "lastCheck": "2026-06-16T10:30:00" },
    "2": { "online": false, "latencyMs": null, "message": "连接超时", "lastCheck": "2026-06-16T10:30:00" }
  }
}
```

**新增接口**: `GET /agents/{id}/health`

功能：检测单个智能体的健康状态，逻辑同上但只返回一个智能体的状态。

**实现细节**：
- 在 `AgentServiceImpl` 中新增 `checkAgentHealth(Agent agent)` 方法
- 需要注入 `AiModelMapper` 或 `AiModelService` 来获取模型配置
- 使用 `RestTemplate` 发送检测请求，设置超时 3 秒
- SKILL 类型智能体共享同一个运行时，只需检测一次即可缓存结果

### 二、后端：迁移运行时健康检测到 AgentController

**文件**: `hczk-ai-agent-server/src/main/java/com/hczk/hczkaiagentserver/controller/PlatformController.java`

**变更**：
- 将 `checkRuntimeHealth()` 方法迁移到 `AgentController`
- 保留原接口路径 `/platform/runtime/health` 作为兼容（可选），或直接删除
- 在 `PlatformController` 中删除 `checkRuntimeHealth` 和 `buildHealthResponse` 方法
- 删除 `runtimeHealthUrl` 配置项和 `RestTemplate` 字段（如果不再被其他方法使用）

### 三、前端：重构 Agents.vue，集成健康检测

**文件**: `hczk-agent-web/src/views/admin/Agents.vue`

**变更**：

1. **新增健康状态数据**：
   - 添加 `agentHealthMap` ref，存储每个智能体的健康状态
   - 添加 `loadHealthStatus()` 方法，调用 `GET /agents/health` 获取所有智能体健康状态
   - 添加自动刷新逻辑（每 30 秒轮询一次）

2. **增强智能体卡片**：
   - 在卡片右上角添加健康状态指示灯（绿色=在线，红色=离线，黄色=降级）
   - 在卡片底部显示延迟信息和最后检测时间
   - 添加"检测"按钮，手动触发单个智能体健康检测

3. **新增统计概览区域**：
   - 在页面顶部添加统计卡片行，展示：智能体总数、在线数、离线数、总调用次数
   - 从 Monitor.vue 迁移统计逻辑

4. **新增自动刷新控制**：
   - 添加自动刷新开关按钮
   - 显示上次更新时间

### 四、前端：删除 Monitor.vue

**文件**: `hczk-agent-web/src/views/admin/Monitor.vue`

**操作**：删除该文件

### 五、前端：更新路由

**文件**: `hczk-agent-web/src/router/index.js`

**变更**：
- 删除 `{ path: 'monitor', name: 'admin-monitor', component: () => import('@/views/admin/Monitor.vue') }` 路由项

### 六、前端：更新侧边栏导航

**文件**: `hczk-agent-web/src/views/admin/Layout.vue`

**变更**：
- 删除 `{ path: '/admin/monitor', name: '智能体监控', icon: Activity }` 导航项
- 保留"智能体管理"导航项

---

## 文件变更清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `AgentController.java` | 修改 | 新增 `/agents/health` 和 `/agents/{id}/health` 接口 |
| `AgentService.java` | 修改 | 新增健康检测方法签名 |
| `AgentServiceImpl.java` | 修改 | 实现按类型检测智能体健康状态的逻辑 |
| `PlatformController.java` | 修改 | 删除运行时健康检测相关代码 |
| `Agents.vue` | 修改 | 重构为集成健康检测的统一管理界面 |
| `Monitor.vue` | 删除 | 不再需要独立的监控页面 |
| `router/index.js` | 修改 | 删除 monitor 路由 |
| `Layout.vue` | 修改 | 删除"智能体监控"导航项 |

---

## 假设与决策

1. **健康检测粒度**：SKILL 类型智能体共享同一个通用运行时，所有 SKILL 类型智能体的健康状态相同（取决于运行时是否在线）
2. **检测超时**：设置 3 秒超时，避免长时间阻塞
3. **批量检测性能**：`/agents/health` 接口对 ENDPOINT 类型智能体并行检测，避免串行等待
4. **MODEL 类型检测方式**：使用模型 API 的 `/models` 端点（OpenAI 兼容格式）进行轻量级可达性检测，不消耗 Token
5. **保留 Skill 管理页面**：Skills.vue 保持独立，不在此次重构范围内
6. **保留绑定管理页面**：Bindings.vue 保持独立，不在此次重构范围内

---

## 验证步骤

1. 启动后端服务，调用 `GET /agents/health` 确认返回正确的健康状态
2. 调用 `GET /agents/{id}/health` 确认单个智能体检测正常
3. 访问 `/admin/agents` 页面，确认：
   - 智能体卡片显示健康状态指示灯
   - 统计概览区域显示正确数据
   - 自动刷新功能正常
   - 手动检测按钮可用
4. 确认 `/admin/monitor` 路由已不可访问
5. 确认侧边栏不再显示"智能体监控"入口
6. 确认原有智能体 CRUD 功能（创建/编辑/删除/启停）不受影响
