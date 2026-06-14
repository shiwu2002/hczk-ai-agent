# 知识库 API 接口文档

> 基础路径：`/knowledge`，统一返回 `Result<T>` 格式：`{ code: 200, data: ..., message: ... }`

---

## 目录

- [核心概念](#核心概念)
- [一、检索接口](#一检索接口)
- [二、摄入接口（入库）](#二摄入接口入库)
  - [2.1 文件上传（推荐）](#21-文件上传推荐)
  - [2.2 纯文本摄入](#22-纯文本摄入)
  - [2.3 JSON 结构化数据摄入](#23-json-结构化数据摄入)
- [三、集合管理](#三集合管理)
- [四、分块浏览](#四分块浏览)
- [五、反馈与评分](#五反馈与评分)
- [六、知识库归属管理](#六知识库归属管理)
- [调用示例](#调用示例)

---

## 核心概念

### agentId 归属标识

所有接口的归属标识，格式如下：

| 类型 | 格式 | 示例 |
|------|------|------|
| 智能体 | `agent_{智能体ID}` | `agent_1` |
| 用户 | `user_{用户ID}` | `user_5` |

Milvus 集合名自动生成规则：`kb_{agentId}_{collectionName}`

例如 `agent_1` + `default` → 集合名 `kb_agent_1_default`

### docType 文档类型

| 值 | 说明 | 适用场景 |
|----|------|---------|
| `auto` | 自动检测 QA 格式，检测不到则按散文分块 | 默认值，不确定时使用 |
| `qa` | 强制问答模式 | Excel 第一列=问题/第二列=答案；或文本含 Q:/问: 格式 |
| `prose` | 强制论文/散文模式 | 论文、长文档、大段连续文本 |

### chunkType 分块类型

| 值 | 说明 | content 字段内容 |
|----|------|------------------|
| `qa` | 问答对 | 答案部分 |
| `prose` | 散文/长文本 | 分块后的文本片段 |

### 检索策略 (strategy)

| 策略 | 说明 |
|------|------|
| `hybrid` | 混合检索：同时搜索 content_vector + question_vector，合并排序 |
| `vector` | 向量检索：仅 content 向量匹配 |
| `keyword` | 关键词检索：中文分词 + like 匹配 |
| `fallback` | 兜底回复：无结果时返回默认 FAQ |

---

## 一、检索接口

### POST /knowledge/retrieve

混合检索（核心接口），支持向量+关键词多路召回。

**请求体：**

```json
{
  "agentId": "agent_1",
  "collection": "default",
  "query": "世界上最帅的人是谁",
  "topK": 5
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| agentId | String | 是 | 归属 ID |
| collection | String | 否 | 集合名，默认 `default` |
| query | String | 是 | 查询文本 |
| topK | Integer | 否 | 返回条数，默认 5，最大 50 |

**响应：**

```json
{
  "code": 200,
  "data": {
    "strategy": "hybrid",
    "confidence": 0.7,
    "fallbackUsed": false,
    "results": [
      {
        "content": "何海峰",
        "score": 0.83,
        "metadata": {
          "matchType": "question",
          "chunkType": "qa",
          "question": "世界上最帅的人是谁",
          "source": "xlsx",
          "title": "工作簿1.xlsx",
          "chunkIndex": 0,
          "hitCount": 2,
          "adoptCount": 0,
          "relevanceScore": 1.0
        }
      }
    ]
  }
}
```

**响应字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| strategy | String | 检索策略：hybrid/vector/keyword/fallback |
| confidence | Double | 置信度阈值（0~1），低于此值的回答可能不可靠 |
| fallbackUsed | Boolean | 是否降级到兜底回复 |
| results[].content | String | 分块内容（QA 类型为答案，Prose 类型为文本） |
| results[].score | Double | 最终得分（0~1），越高越相关 |
| results[].metadata.matchType | String | 命中来源：content/question/keyword |
| results[].metadata.chunkType | String | 分块类型：qa/prose |
| results[].metadata.question | String | 问题字段（仅 qa 类型有值） |
| results[].metadata.source | String | 数据来源文件类型 |
| results[].metadata.title | String | 文档标题 |
| results[].metadata.hitCount | Long | 被检索命中次数 |
| results[].metadata.adoptCount | Long | 被采纳次数 |
| results[].metadata.relevanceScore | Double | 相关性评分（评分重算后更新） |

---

## 二、摄入接口（入库）

### 2.1 文件上传（推荐）

#### POST /knowledge/ingest/file

单文件上传摄入。

**参数（multipart/form-data）：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | MultipartFile | **是** | 文件（支持 .txt / .pdf / .docx / .xlsx，单文件最大 50MB） |
| agentId | String | **是** | 归属 ID（如 `agent_1`） |
| collection | String | 否 | 集合名，默认 `default` |
| title | String | 否 | 文档标题，默认使用文件名 |
| docType | String | 否 | 文档类型：`auto`(默认) / `qa` / `prose` |

**响应：**

```json
{ "code": 200, "data": { "totalChunks": 25, "ingestedChunks": 24, "skippedChunks": 1 } }
```

> `skippedChunks` 包含 Embed 失败数和重复内容数（相同 content_hash 不重复写入）

#### POST /knowledge/ingest/files

批量文件上传摄入。

**参数（multipart/form-data）：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| files | MultipartFile[] | **是** | 文件数组 |
| agentId | String | **是** | 归属 ID |
| collection | String | 否 | 集合名，默认 `default` |
| docType | String | 否 | 文档类型：`auto`(默认) / `qa` / `prose` |

**响应：**

```json
{ "code": 200, "data": [{ "totalChunks": 10, "ingestedChunks": 10, "skippedChunks": 0 }, ...] }
```

**支持的文件格式及处理方式：**

| 格式 | auto 模式 | qa 模式 | prose 模式 |
|------|----------|---------|-----------|
| .txt | 检测 Q:/问: → QA，否则 prose | 按 Q:/问: 格式拆分 | 按句子分块 |
| .pdf | 同上 | 同上 | 提取全文后按句子分块 |
| .docx | 同上 | 同上 | 提取段落+表格后按句子分块 |
| .xlsx | 按行拼接为文本再分块 | 第一列=问题，第二列=答案 | 按行拼接为文本再按句子分块 |

### 2.2 纯文本摄入

### POST /knowledge/ingest

直接提交纯文本进行分块和向量化入库。

**请求体：**

```json
{
  "agentId": "agent_1",
  "collection": "default",
  "text": "要入库的文本内容...",
  "source": "api",
  "title": "文档标题"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| agentId | String | **是** | 归属 ID |
| collection | String | 否 | 集合名，默认 `default` |
| text | String | **是** | 文本内容 |
| source | String | 否 | 来源标识，默认 `api` |
| title | String | 否 | 标题 |

**响应：** 同 2.1

### 2.3 JSON 结构化数据摄入

### POST /knowledge/ingest/json

提交结构化 JSON 数组，每条记录独立分块入库。

**请求体：**

```json
{
  "agentId": "agent_1",
  "collection": "default",
  "data": [
    { "content": "第一条内容的答案", "source": "api", "title": "Q1" },
    { "content": "第二条内容的答案", "source": "api", "title": "Q2" }
  ]
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| agentId | String | **是** | 归属 ID |
| collection | String | 否 | 集合名，默认 `default` |
| data | Array | **是** | 数据数组，每项包含 content/source/title |

**响应：** 同 2.1

---

## 三、集合管理

### GET /knowledge/collections

列出所有知识集合及其归属信息。

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| agentId | String | 否 | 指定归属 ID 过滤 |

**响应：**

```json
{
  "code": 200,
  "data": [
    {
      "name": "kb_agent_1_default",
      "rowCount": 150,
      "agentId": "agent_1",
      "collectionName": "default",
      "ownerType": "AGENT",
      "ownerId": 1,
      "ownerName": "智能体名称"
    }
  ]
}
```

### DELETE /knowledge/collections/{name}

删除指定集合（同时删除 Milvus 集合和归属记录）。

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| agentId | String | **是** | 归属 ID |

**路径参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| name | String | 集合名（不含 kb_ 前缀），如 `default` |

**响应：** `{ "code": 200 }`

---

## 四、分块浏览

### GET /knowledge/collections/{name}/chunks

浏览集合中的所有分块数据。

**路径参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| name | String | 集合名（不含 kb_ 前缀） |

**查询参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| agentId | String | **是** | - | 归属 ID |
| chunkType | String | 否 | - | 过滤分块类型：`qa` / `prose` |
| source | String | 否 | - | 过滤来源：`xlsx` / `txt` / `pdf` / `api` 等 |
| limit | Integer | 否 | `100` | 返回条数，最大 1000 |

**响应：**

```json
{
  "code": 200,
  "data": [
    {
      "id": "a1b2c3...",
      "content": "何海峰",
      "question": "世界上最帅的人是谁",
      "source": "xlsx",
      "title": "工作簿1.xlsx",
      "chunkIndex": 0,
      "chunkType": "qa",
      "hitCount": 5,
      "adoptCount": 2,
      "relevanceScore": 1.25,
      "createdAt": 1718400000000,
      "ingestTime": 1718400000000
    }
  ]
}
```

---

## 五、反馈与评分

### POST /knowledge/chunks/{id}/adopt

标记某条分块被采纳。用于优化相关性评分——被采纳多的分块在后续检索中排名更高。

**路径参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | String | 分块的 content_hash（SHA256） |

**请求体：**

```json
{ "agentId": "agent_1", "collection": "default" }
```

**响应：** `{ "code": 200 }`

### POST /knowledge/recalculate

重算相关性评分。根据 hitCount 和 adoptCount 重新计算每条分块的 relevanceScore。

**请求体：**

```json
// 重算该对象的所有集合
{ "agentId": "agent_1" }

// 仅重算指定集合
{ "agentId": "agent_1", "collection": "default" }
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| agentId | String | **是** | 归属 ID |
| collection | String | 否 | 指定集合名，留空则全部重算 |

**响应：**

```json
{ "code": 200, "data": 150 }
```
> 返回值为已更新的数据条数

---

## 六、知识库归属管理

### GET /knowledge/bases

查询知识库归属记录。

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| ownerType | String | 否 | 过滤归属类型：`AGENT` / `USER` |
| ownerId | Long | 否 | 过滤归属对象 ID（需配合 ownerType 使用） |

**响应：**

```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "name": "default",
      "ownerType": "AGENT",
      "ownerId": 1,
      "agentId": "agent_1",
      "collectionName": "default",
      "createdAt": 1718400000000
    }
  ]
}
```

### POST /knowledge/bases

创建知识库归属记录（通常由摄入接口自动创建，无需手动调用）。

**请求体：**

```json
{
  "ownerType": "AGENT",
  "ownerId": 1,
  "collectionName": "default",
  "name": "我的知识库"
}
```

### DELETE /knowledge/bases/{id}

删除知识库归属记录（不删除 Milvus 数据）。

### GET /knowledge/owners/agents

获取可绑定的活跃智能体列表。

**响应：** `{ "code": 200, "data": [{ "id": 1, "name": "智能体名称", ... }] }`

### GET /knowledge/owners/users

获取可绑定的活跃用户列表。

**响应：** `{ "code": 200, "data": [{ "id": 1, "username": "用户名", ... }] }`

---

## 调用示例

```bash
API_BASE="http://localhost:8080"
TOKEN="your-jwt-token"

# ========== 1. 检索 ==========
curl -X POST "$API_BASE/knowledge/retrieve" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"agentId":"agent_1","collection":"default","query":"用户问题","topK":5}'

# ========== 2. 上传文件入库（QA 模式） ==========
curl -X POST "$API_BASE/knowledge/ingest/file" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@qa.xlsx" \
  -F "agentId=agent_1" \
  -F "collection=default" \
  -F "docType=qa"

# ========== 3. 上传文件入库（论文模式） ==========
curl -X POST "$API_BASE/knowledge/ingest/file" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@paper.pdf" \
  -F "agentId=agent_1" \
  -F "collection=papers" \
  -F "docType=prose"

# ========== 4. 批量上传 ==========
curl -X POST "$API_BASE/knowledge/ingest/files" \
  -H "Authorization: Bearer $TOKEN" \
  -F "files=@file1.txt" \
  -F "files=@file2.pdf" \
  -F "agentId=user_5" \
  -F "docType=auto"

# ========== 5. 纯文本入库 ==========
curl -X POST "$API_BASE/knowledge/ingest" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "agentId":"agent_1",
    "text":"这是一段需要入库的知识文本...",
    "source":"manual",
    "title":"手动录入"
  }'

# ========== 6. JSON 结构化数据入库 ==========
curl -X POST "$API_BASE/knowledge/ingest/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "agentId":"agent_1",
    "data":[
      {"content":"答案A","title":"Q1"},
      {"content":"答案B","title":"Q2"}
    ]
  }'

# ========== 7. 列出集合 ==========
curl -X GET "$API_BASE/knowledge/collections?agentId=agent_1" \
  -H "Authorization: Bearer $TOKEN"

# ========== 8. 浏览分块 ==========
curl -X GET "$API_BASE/knowledge/collections/default/chunks?agentId=agent_1&chunkType=qa&limit=50" \
  -H "Authorization: Bearer $TOKEN"

# ========== 9. 标记采纳（用户觉得回答有用时调用） ==========
curl -X POST "$API_BASE/knowledge/chunks/a1b2c3d4e5f6/adopt" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"agentId":"agent_1","collection":"default"}'

# ========== 10. 重算相关性评分 ==========
curl -X POST "$API_BASE/knowledge/recalculate" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"agentId":"agent_1"}'

# ========== 11. 删除集合 ==========
curl -X DELETE "$API_BASE/knowledge/collections/default?agentId=agent_1" \
  -H "Authorization: Bearer $TOKEN"
```

---

## 错误码说明

| HTTP Code | code 字段 | 说明 |
|-----------|----------|------|
| 200 | 200 | 成功 |
| 200 | 非 200 | 业务错误，message 字段包含错误描述 |
| 400 | - | 请求参数错误 |
| 401 | - | 未授权（Token 无效或缺失） |
| 415 | - | 不支持的文件格式 |
| 500 | - | 服务端内部错误 |
