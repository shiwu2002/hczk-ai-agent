package com.hczk.hczkaiagentserver.knowledge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hczk.hczkaiagentserver.common.Result;
import com.hczk.hczkaiagentserver.entity.Agent;
import com.hczk.hczkaiagentserver.entity.KnowledgeBase;
import com.hczk.hczkaiagentserver.entity.User;
import com.hczk.hczkaiagentserver.knowledge.dto.*;
import com.hczk.hczkaiagentserver.knowledge.ingester.Ingester;
import com.hczk.hczkaiagentserver.knowledge.milvus.MilvusManager;
import com.hczk.hczkaiagentserver.knowledge.parser.DocumentParser;
import com.hczk.hczkaiagentserver.knowledge.retriever.Retriever;
import com.hczk.hczkaiagentserver.knowledge.scorer.RelevanceScorer;
import com.hczk.hczkaiagentserver.mapper.AgentMapper;
import com.hczk.hczkaiagentserver.mapper.UserMapper;
import com.hczk.hczkaiagentserver.service.KnowledgeBaseService;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.QueryResp;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final Ingester ingester;
    private final Retriever retriever;
    private final RelevanceScorer relevanceScorer;
    private final MilvusManager milvusManager;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentParser documentParser;
    private final AgentMapper agentMapper;
    private final UserMapper userMapper;

    // ==================== 知识库归属管理 ====================

    /**
     * GET /knowledge/bases - 获取所有知识库归属记录
     */
    @GetMapping("/bases")
    public Result<List<KnowledgeBase>> listKnowledgeBases(
            @RequestParam(required = false) String ownerType,
            @RequestParam(required = false) String ownerId) {
        if (ownerType != null && ownerId != null) {
            return Result.success(knowledgeBaseService.getByOwnerId(ownerType, ownerId));
        }
        if (ownerType != null) {
            return Result.success(knowledgeBaseService.getByOwnerType(ownerType));
        }
        return Result.success(knowledgeBaseService.getAllKnowledgeBases());
    }

    /**
     * POST /knowledge/bases - 创建知识库归属记录
     */
    @PostMapping("/bases")
    public Result<KnowledgeBase> createKnowledgeBase(@RequestBody KnowledgeBase knowledgeBase) {
        // 根据 ownerType 和 ownerId 生成 agentId
        String agentId = buildAgentId(knowledgeBase.getOwnerType(), knowledgeBase.getOwnerId());
        knowledgeBase.setAgentId(agentId);
        if (knowledgeBase.getCollectionName() == null) {
            knowledgeBase.setCollectionName("default");
        }
        if (knowledgeBase.getName() == null) {
            knowledgeBase.setName(knowledgeBase.getCollectionName());
        }
        KnowledgeBase created = knowledgeBaseService.createKnowledgeBase(knowledgeBase);
        return Result.success(created);
    }

    /**
     * DELETE /knowledge/bases/{id} - 删除知识库归属记录
     */
    @DeleteMapping("/bases/{id}")
    public Result<Void> deleteKnowledgeBase(@PathVariable Long id) {
        knowledgeBaseService.deleteKnowledgeBase(id);
        return Result.success();
    }

    /**
     * GET /knowledge/owners/agents - 获取可绑定的智能体列表
     */
    @GetMapping("/owners/agents")
    public Result<List<Agent>> listAgentsForOwner() {
        return Result.success(agentMapper.selectList(
                new LambdaQueryWrapper<Agent>().eq(Agent::getStatus, 0)));
    }

    /**
     * GET /knowledge/owners/users - 获取可绑定的用户列表
     */
    @GetMapping("/owners/users")
    public Result<List<User>> listUsersForOwner() {
        return Result.success(userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getStatus, 0)));
    }

    // ==================== 知识库操作 ====================

    /**
     * POST /knowledge/retrieve - Retrieve knowledge
     */
    @PostMapping("/retrieve")
    public Result<RetrieveResponse> retrieve(@Valid @RequestBody RetrieveRequest request) {
        RetrieveResponse response = retriever.retrieve(
                request.getAgentId(),
                request.getCollection(),
                request.getQuery(),
                request.getTopK()
        );
        return Result.success(response);
    }

    /**
     * POST /knowledge/ingest - Ingest plain text document
     */
    @PostMapping("/ingest")
    public Result<IngestResponse> ingest(@Valid @RequestBody IngestRequest request) {
        IngestResponse response = ingester.ingestText(
                request.getAgentId(),
                request.getCollection(),
                request.getText(),
                request.getSource(),
                request.getTitle()
        );
        // 自动绑定归属关系
        ensureKnowledgeBaseBinding(request.getAgentId(), request.getCollection());
        return Result.success(response);
    }

    /**
     * POST /knowledge/ingest/json - Ingest JSON array data
     */
    @PostMapping("/ingest/json")
    public Result<IngestResponse> ingestJson(@Valid @RequestBody IngestJsonRequest request) {
        IngestResponse response = ingester.ingestJson(
                request.getAgentId(),
                request.getCollection(),
                request.getData()
        );
        // 自动绑定归属关系
        ensureKnowledgeBaseBinding(request.getAgentId(), request.getCollection());
        return Result.success(response);
    }

    /**
     * POST /knowledge/ingest/file - 上传文档文件摄入
     * 支持 Word(.docx)、PDF(.pdf)、TXT(.txt)、Excel(.xlsx)
     * @param docType 文档类型: "auto"自动检测, "qa"问答, "prose"论文/散文
     */
    @PostMapping("/ingest/file")
    public Result<IngestResponse> ingestFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("agentId") String agentId,
            @RequestParam(value = "collection", defaultValue = "default") String collection,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "docType", defaultValue = "auto") String docType) {
        if (file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (!documentParser.isSupported(filename)) {
            return Result.error("不支持的文件类型，仅支持 .txt .pdf .docx .xlsx");
        }

        String fileType = documentParser.getFileType(filename);
        String docTitle = (title != null && !title.isBlank()) ? title : filename;

        // Excel + QA 模式：使用专用 QA 解析
        if ("xlsx".equals(fileType) && "qa".equals(docType)) {
            DocumentParser.ParsedDocument parsed;
            try {
                parsed = documentParser.parseXlsxAsQA(file);
            } catch (Exception e) {
                log.error("Excel QA 解析失败: {}", e.getMessage());
                return Result.error("Excel QA 解析失败: " + e.getMessage());
            }
            if (parsed.qaPairs() == null || parsed.qaPairs().isEmpty()) {
                return Result.error("Excel 内容为空");
            }
            IngestResponse response = ingester.ingestQAPairs(agentId, collection, parsed.qaPairs(), fileType, docTitle);
            ensureKnowledgeBaseBinding(agentId, collection);
            return Result.success(response);
        }

        // 其他情况：v11 走页感知解析+入库（保留页码、章节、表格结构）
        DocumentParser.ParsedDocumentWithPages parsed;
        try {
            parsed = documentParser.parseWithPages(file);
        } catch (Exception e) {
            log.error("文档解析失败: {}", e.getMessage());
            return Result.error("文档解析失败: " + e.getMessage());
        }

        if (parsed.pages() == null || parsed.pages().isEmpty()) {
            return Result.error("文档内容为空");
        }

        IngestResponse response = ingester.ingestPages(agentId, collection, parsed.pages(), fileType, docTitle, docType);
        ensureKnowledgeBaseBinding(agentId, collection);
        return Result.success(response);
    }

    /**
     * POST /knowledge/ingest/files - 批量上传文档文件摄入
     */
    @PostMapping("/ingest/files")
    public Result<List<IngestResponse>> ingestFiles(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("agentId") String agentId,
            @RequestParam(value = "collection", defaultValue = "default") String collection,
            @RequestParam(value = "docType", defaultValue = "auto") String docType) {
        List<IngestResponse> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty() || !documentParser.isSupported(file.getOriginalFilename())) {
                continue;
            }
            try {
                String fileType = documentParser.getFileType(file.getOriginalFilename());
                String docTitle = file.getOriginalFilename();

                // Excel + QA 模式
                if ("xlsx".equals(fileType) && "qa".equals(docType)) {
                    DocumentParser.ParsedDocument parsed = documentParser.parseXlsxAsQA(file);
                    if (parsed.qaPairs() != null && !parsed.qaPairs().isEmpty()) {
                        IngestResponse resp = ingester.ingestQAPairs(agentId, collection, parsed.qaPairs(), fileType, docTitle);
                        results.add(resp);
                    }
                    continue;
                }

                DocumentParser.ParsedDocumentWithPages parsed = documentParser.parseWithPages(file);
                if (parsed.pages() != null && !parsed.pages().isEmpty()) {
                    IngestResponse resp = ingester.ingestPages(
                            agentId, collection, parsed.pages(),
                            fileType, docTitle, docType);
                    results.add(resp);
                }
            } catch (Exception e) {
                log.warn("文件 {} 解析失败: {}", file.getOriginalFilename(), e.getMessage());
            }
        }
        ensureKnowledgeBaseBinding(agentId, collection);
        return Result.success(results);
    }

    /**
     * GET /knowledge/collections - List all collections
     */
    @GetMapping("/collections")
    public Result<List<CollectionInfo>> listCollections(
            @RequestParam(required = false) String agentId) {
        List<String> collectionNames = milvusManager.listCollections(agentId);
        List<CollectionInfo> result = new ArrayList<>();
        for (String name : collectionNames) {
            long rowCount = milvusManager.getRowCount(name);
            String extractedAgentId = extractAgentId(name);
            String collName = extractCollectionName(name);

            // 查询归属信息
            KnowledgeBase kb = knowledgeBaseService.findByAgentIdAndCollection(extractedAgentId, collName);
            String ownerType = kb != null ? kb.getOwnerType() : null;
            String ownerId = kb != null ? kb.getOwnerId() : null;
            String ownerName = resolveOwnerName(ownerType, ownerId);

            result.add(new CollectionInfo(name, rowCount, extractedAgentId, collName, ownerType, ownerId, ownerName));
        }
        return Result.success(result);
    }

    /**
     * DELETE /knowledge/collections/{name} - Delete collection
     */
    @DeleteMapping("/collections/{name}")
    public Result<Void> deleteCollection(
            @PathVariable String name,
            @RequestParam String agentId) {
        String fullCollectionName = milvusManager.buildCollectionName(agentId, name);
        milvusManager.dropCollection(fullCollectionName);
        // 同步删除归属记录
        KnowledgeBase kb = knowledgeBaseService.findByAgentIdAndCollection(agentId, name);
        if (kb != null) {
            knowledgeBaseService.deleteKnowledgeBase(kb.getId());
        }
        return Result.success();
    }

    /**
     * GET /knowledge/collections/{name}/chunks - Browse chunks
     */
    @GetMapping("/collections/{name}/chunks")
    public Result<List<ChunkItem>> browseChunks(
            @PathVariable String name,
            @RequestParam String agentId,
            @RequestParam(required = false) String chunkType,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "100") int limit) {
        String fullCollectionName = milvusManager.buildCollectionName(agentId, name);
        milvusManager.ensureCollection(fullCollectionName);

        // Build filter
        List<String> filters = new ArrayList<>();
        filters.add("id != \"\"");
        if (chunkType != null && !chunkType.isEmpty()) {
            filters.add("chunk_type == \"" + chunkType.replace("\"", "\\\"") + "\"");
        }
        if (source != null && !source.isEmpty()) {
            filters.add("source == \"" + source.replace("\"", "\\\"") + "\"");
        }
        String filter = String.join(" and ", filters);

        QueryReq queryReq = QueryReq.builder()
                .collectionName(fullCollectionName)
                .filter(filter)
                .limit((long) Math.min(limit, 1000))
                .outputFields(List.of("id", "content", "source", "title", "chunk_index",
                        "question", "content_hash", "created_at", "ingest_time",
                        "hit_count", "adopt_count", "relevance_score", "chunk_type"))
                .build();

        QueryResp queryResp = milvusManager.getClient().query(queryReq);

        List<ChunkItem> chunks = new ArrayList<>();
        for (QueryResp.QueryResult row : queryResp.getQueryResults()) {
            Map<String, Object> entity = row.getEntity();
            chunks.add(ChunkItem.builder()
                    .id(entity.getOrDefault("id", "").toString())
                    .content(entity.getOrDefault("content", "").toString())
                    .source(entity.getOrDefault("source", "").toString())
                    .title(entity.getOrDefault("title", "").toString())
                    .chunkIndex(entity.containsKey("chunk_index") ? ((Number) entity.get("chunk_index")).intValue() : 0)
                    .question(entity.getOrDefault("question", "").toString())
                    .contentHash(entity.getOrDefault("content_hash", "").toString())
                    .createdAt(entity.containsKey("created_at") ? ((Number) entity.get("created_at")).longValue() : 0L)
                    .ingestTime(entity.containsKey("ingest_time") ? ((Number) entity.get("ingest_time")).longValue() : 0L)
                    .hitCount(entity.containsKey("hit_count") ? ((Number) entity.get("hit_count")).longValue() : 0L)
                    .adoptCount(entity.containsKey("adopt_count") ? ((Number) entity.get("adopt_count")).longValue() : 0L)
                    .relevanceScore(entity.containsKey("relevance_score") ? ((Number) entity.get("relevance_score")).doubleValue() : 1.0)
                    .chunkType(entity.getOrDefault("chunk_type", "prose").toString())
                    .build());
        }
        return Result.success(chunks);
    }

    /**
     * POST /knowledge/chunks/{id}/adopt - Mark chunk as adopted
     */
    @PostMapping("/chunks/{id}/adopt")
    public Result<Void> adoptChunk(
            @PathVariable String id,
            @Valid @RequestBody AdoptRequest request) {
        String fullCollectionName = milvusManager.buildCollectionName(request.getAgentId(), request.getCollection());
        relevanceScorer.markAdopted(fullCollectionName, id);
        return Result.success();
    }

    /**
     * POST /knowledge/recalculate - Recalculate relevance scores
     */
    @PostMapping("/recalculate")
    public Result<Integer> recalculate(@Valid @RequestBody RecalculateRequest request) {
        int updated;
        if (request.getCollection() != null && !request.getCollection().isEmpty()) {
            String fullCollectionName = milvusManager.buildCollectionName(request.getAgentId(), request.getCollection());
            updated = relevanceScorer.recalculateForCollection(fullCollectionName);
        } else {
            updated = relevanceScorer.recalculateForAgent(request.getAgentId());
        }
        return Result.success(updated);
    }

    // ==================== 私有方法 ====================

    /**
     * 从物理集合名提取 agentId
     * 格式: kb_{agentId}_{collectionName}，agentId 为纯数字雪花ID
     */
    private String extractAgentId(String collectionName) {
        if (collectionName.startsWith("kb_")) {
            String rest = collectionName.substring(3);
            int underscoreIndex = rest.indexOf('_');
            if (underscoreIndex > 0) {
                return rest.substring(0, underscoreIndex);
            }
        }
        return "";
    }

    /**
     * 从物理集合名提取逻辑集合名
     */
    private String extractCollectionName(String collectionName) {
        if (collectionName.startsWith("kb_")) {
            String rest = collectionName.substring(3);
            int underscoreIndex = rest.indexOf('_');
            if (underscoreIndex > 0) {
                return rest.substring(underscoreIndex + 1);
            }
        }
        return "";
    }

    /**
     * 根据 ownerId 生成 Milvus 用的 agentId
     * ownerId 即为雪花ID字符串，直接使用
     */
    private String buildAgentId(String ownerType, String ownerId) {
        return ownerId;
    }

    /**
     * 摄入时自动确保归属关系存在
     * agentId 为雪花ID字符串，通过 user_id 查找用户
     */
    private void ensureKnowledgeBaseBinding(String agentId, String collectionName) {
        try {
            KnowledgeBase existing = knowledgeBaseService.findByAgentIdAndCollection(agentId, collectionName);
            if (existing != null) {
                return;
            }
            // agentId 即为用户的雪花ID
            KnowledgeBase kb = new KnowledgeBase();
            kb.setName(collectionName);
            kb.setOwnerType("USER");
            kb.setOwnerId(agentId);
            kb.setAgentId(agentId);
            kb.setCollectionName(collectionName);
            knowledgeBaseService.createKnowledgeBase(kb);
        } catch (Exception e) {
            log.warn("自动绑定知识库归属失败: {}", e.getMessage());
        }
    }

    /**
     * 解析归属对象名称
     */
    private String resolveOwnerName(String ownerType, String ownerId) {
        if (ownerType == null || ownerId == null) {
            return null;
        }
        try {
            if ("USER".equals(ownerType)) {
                User user = userMapper.selectOne(
                        new LambdaQueryWrapper<User>().eq(User::getUserId, ownerId));
                return user != null ? user.getUsername() : null;
            }
        } catch (Exception e) {
            log.warn("解析归属对象名称失败: {}", e.getMessage());
        }
        return null;
    }
}
