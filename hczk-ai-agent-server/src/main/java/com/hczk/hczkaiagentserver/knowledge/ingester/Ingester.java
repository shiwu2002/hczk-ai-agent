package com.hczk.hczkaiagentserver.knowledge.ingester;

import com.hczk.hczkaiagentserver.entity.IngestLog;
import com.hczk.hczkaiagentserver.entity.ServiceHealthMetric;
import com.hczk.hczkaiagentserver.knowledge.chunker.Chunker;
import com.hczk.hczkaiagentserver.knowledge.config.EmbeddingProperties;
import com.hczk.hczkaiagentserver.knowledge.config.IngestProperties;
import com.hczk.hczkaiagentserver.knowledge.dto.IngestJsonRequest;
import com.hczk.hczkaiagentserver.knowledge.dto.IngestResponse;
import com.hczk.hczkaiagentserver.knowledge.embedder.Embedder;
import com.hczk.hczkaiagentserver.knowledge.llm.LlmPreprocessor;
import com.hczk.hczkaiagentserver.knowledge.milvus.MilvusManager;
import com.hczk.hczkaiagentserver.knowledge.parser.DocumentParser;
import com.hczk.hczkaiagentserver.knowledge.util.MilvusDataConverter;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.QueryResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class Ingester {

    private final MilvusManager milvusManager;
    private final Embedder embedder;
    private final IngestProperties ingestProperties;
    private final EmbeddingProperties embeddingProperties;
    private final LlmPreprocessor llmPreprocessor;
    private final com.hczk.hczkaiagentserver.mapper.IngestLogMapper ingestLogMapper;
    private final com.hczk.hczkaiagentserver.mapper.ServiceHealthMetricMapper serviceHealthMetricMapper;

    public IngestResponse ingestText(String agentId, String collectionName, String text,
                                      String source, String title) {
        return ingestText(agentId, collectionName, text, source, title, "auto");
    }

    public IngestResponse ingestText(String agentId, String collectionName, String text,
                                      String source, String title, String docType) {
        String fullCollectionName = milvusManager.buildCollectionName(agentId, collectionName);
        milvusManager.ensureCollection(fullCollectionName);
        Chunker chunker = new Chunker(ingestProperties.getChunkSize(), ingestProperties.getChunkOverlapSentences());

        // v10：入库前先通过 LLM 进行一轮总结与语义切割
        List<Chunker.Chunk> chunks = splitWithLlmPreprocess(chunker, text, docType, source, title);
        return ingestChunks(fullCollectionName, chunks, source, title);
    }

    /**
     * v11 新增：带页码和章节信息的入库方法
     * 接收解析后的页面列表，走 LLM 页感知预处理，写入溯源信息
     *
     * @param agentId       智能体ID
     * @param collectionName 集合名
     * @param pages          解析后的页面列表（含页码、章节、表格标记）
     * @param source         来源
     * @param title          标题
     * @param docType        文档类型
     */
    public IngestResponse ingestPages(String agentId, String collectionName,
                                       List<DocumentParser.ParsedPage> pages,
                                       String source, String title, String docType) {
        long startTime = System.currentTimeMillis();
        IngestLog ingestLog = new IngestLog();
        ingestLog.setAgentId(agentId);
        ingestLog.setCollectionName(collectionName);
        ingestLog.setSource(source);
        ingestLog.setDocType(docType);
        ingestLog.setTotalPages(pages.size());
        ingestLog.setFileType(source != null ? source.substring(source.lastIndexOf('.') + 1).toLowerCase() : "");

        String fullCollectionName = milvusManager.buildCollectionName(agentId, collectionName);
        milvusManager.ensureCollection(fullCollectionName);
        Chunker chunker = new Chunker(ingestProperties.getChunkSize(), ingestProperties.getChunkOverlapSentences());

        List<Chunker.Chunk> chunks;
        boolean llmUsed = false;
        long llmStart = 0;

        // 优先走 LLM 页感知预处理
        if (llmPreprocessor.shouldPreprocessPages(pages, docType)) {
            try {
                log.info("开始 LLM 页感知预处理: source={}, title={}, pages={}", source, title, pages.size());
                llmStart = System.currentTimeMillis();
                List<LlmPreprocessor.SemanticChunk> semanticChunks = llmPreprocessor.summarizeAndSplitWithPages(pages);
                ingestLog.setLlmPreprocessMs(System.currentTimeMillis() - llmStart);

                if (semanticChunks != null && !semanticChunks.isEmpty()) {
                    llmUsed = true;
                    chunks = new ArrayList<>();
                    for (LlmPreprocessor.SemanticChunk sc : semanticChunks) {
                        chunks.add(Chunker.Chunk.builder()
                                .content(sc.content())
                                .question(null)
                                .chunkType("prose")
                                .chunkIndex(sc.chunkIndex())
                                .pageNumber(sc.pageNumbers() != null && !sc.pageNumbers().isEmpty() ? sc.pageNumbers().get(0) : 0)
                                .chapter(sc.chapter())
                                .contextPages(sc.contextPages())
                                .sourceFilename(sc.sourceFilename())
                                .build());
                    }
                    log.info("LLM 页感知预处理成功: {} 页 → {} 个语义块", pages.size(), chunks.size());
                    ingestLog.setLlmPreprocessUsed(true);
                    ingestLog.setTotalChunks(chunks.size());
                    ingestLog.setOcrUsed(false); // OCR 在解析阶段已处理
                    IngestResponse resp = ingestChunks(fullCollectionName, chunks, source, title);
                    ingestLog.setIngestDurationMs(System.currentTimeMillis() - startTime);
                    ingestLog.setStatus("success");
                    ingestLogMapper.insert(ingestLog);
                    return resp;
                }
                log.warn("LLM 页感知预处理返回空结果，回退到规则分块");
            } catch (Exception e) {
                log.error("LLM 页感知预处理失败，回退到规则分块: {}", e.getMessage(), e);
                ingestLog.setErrorMessage("LLM预处理失败: " + e.getMessage());
            }
        }

        // 回退：合并所有页文本走规则分块
        StringBuilder sb = new StringBuilder();
        int firstPage = 0;
        String chapter = "";
        String sourceFilename = "";
        for (DocumentParser.ParsedPage page : pages) {
            if (firstPage == 0) firstPage = page.pageNumber();
            if (chapter.isEmpty() && page.chapter() != null) chapter = page.chapter();
            if (sourceFilename.isEmpty() && page.sourceFilename() != null) sourceFilename = page.sourceFilename();
            sb.append(page.text()).append("\n\n");
        }
        chunks = chunker.split(sb.toString(), docType);
        // 为回退分块附加溯源信息
        for (Chunker.Chunk chunk : chunks) {
            chunk.setPageNumber(firstPage);
            chunk.setChapter(chapter);
            chunk.setSourceFilename(sourceFilename);
        }
        ingestLog.setTotalChunks(chunks.size());
        IngestResponse resp = ingestChunks(fullCollectionName, chunks, source, title);
        ingestLog.setIngestDurationMs(System.currentTimeMillis() - startTime);
        ingestLog.setStatus("success");
        ingestLogMapper.insert(ingestLog);
        return resp;
    }

    /**
     * 直接入库预解析的 QA 对（用于 Excel QA 模式）
     */
    public IngestResponse ingestQAPairs(String agentId, String collectionName,
                                         List<String[]> qaPairs, String source, String title) {
        String fullCollectionName = milvusManager.buildCollectionName(agentId, collectionName);
        milvusManager.ensureCollection(fullCollectionName);
        Chunker chunker = new Chunker(ingestProperties.getChunkSize(), ingestProperties.getChunkOverlapSentences());
        List<Chunker.Chunk> chunks = chunker.splitQAPairs(qaPairs);
        return ingestChunks(fullCollectionName, chunks, source, title);
    }

    public IngestResponse ingestJson(String agentId, String collectionName,
                                      List<IngestJsonRequest.JsonDataItem> data) {
        String fullCollectionName = milvusManager.buildCollectionName(agentId, collectionName);
        milvusManager.ensureCollection(fullCollectionName);
        Chunker chunker = new Chunker(ingestProperties.getChunkSize(), ingestProperties.getChunkOverlapSentences());
        List<Chunker.Chunk> allChunks = new ArrayList<>();
        for (IngestJsonRequest.JsonDataItem item : data) {
            // JSON 入库也支持 LLM 预处理（按 auto 模式判断）
            List<Chunker.Chunk> chunks = splitWithLlmPreprocess(chunker, item.getContent(), "auto", item.getSource(), item.getTitle());
            allChunks.addAll(chunks);
        }
        String source = data.get(0).getSource();
        String title = data.get(0).getTitle();
        return ingestChunks(fullCollectionName, allChunks, source, title);
    }

    /**
     * 分块入口：优先使用 LLM 预处理进行总结与语义切割，失败时回退到规则分块
     *
     * @param chunker 规则分块器
     * @param text    原始文本
     * @param docType 文档类型
     * @param source  来源（仅用于日志）
     * @param title   标题（仅用于日志）
     * @return 分块列表
     */
    private List<Chunker.Chunk> splitWithLlmPreprocess(Chunker chunker, String text,
                                                        String docType, String source, String title) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // 判断是否需要 LLM 预处理
        if (!llmPreprocessor.shouldPreprocess(text, docType)) {
            return chunker.split(text, docType);
        }

        try {
            log.info("开始 LLM 预处理: source={}, title={}, textLen={}, docType={}", source, title, text.length(), docType);
            List<String> semanticChunks = llmPreprocessor.summarizeAndSplit(text);

            if (semanticChunks == null || semanticChunks.isEmpty()) {
                log.warn("LLM 预处理返回空结果，回退到规则分块");
                return chunker.split(text, docType);
            }

            // 将 LLM 产出的语义块转为 Chunk 对象
            List<Chunker.Chunk> chunks = new ArrayList<>();
            for (int i = 0; i < semanticChunks.size(); i++) {
                chunks.add(Chunker.Chunk.builder()
                        .content(semanticChunks.get(i))
                        .question(null)
                        .chunkType("prose")
                        .chunkIndex(i)
                        .build());
            }
            log.info("LLM 预处理成功: 原始 {} 字符 → {} 个语义块", text.length(), chunks.size());
            return chunks;
        } catch (Exception e) {
            log.error("LLM 预处理失败，回退到规则分块: {}", e.getMessage(), e);
            return chunker.split(text, docType);
        }
    }

    private IngestResponse ingestChunks(String collectionName, List<Chunker.Chunk> chunks,
                                         String source, String title) {
        int totalChunks = chunks.size();
        int ingestedChunks = 0;
        int skippedChunks = 0;
        int duplicateChunks = 0;
        long now = System.currentTimeMillis();
        int dim = embeddingProperties.getDimension();
        float[] zeroVector = new float[dim];
        List<Map<String, Object>> insertBatch = new ArrayList<>();

        // 预计算所有 chunk 的 content_hash，批量查询已存在的记录用于去重
        Set<String> incomingHashes = new HashSet<>();
        Map<String, Chunker.Chunk> hashToChunk = new LinkedHashMap<>();
        for (Chunker.Chunk chunk : chunks) {
            String h = sha256(chunk.getContent());
            incomingHashes.add(h);
            if (!hashToChunk.containsKey(h)) {
                hashToChunk.put(h, chunk);
            }
        }

        // 查询已存在的 hashes（分批查询避免 filter 过长）
        Set<String> existingHashes = queryExistingHashes(collectionName, incomingHashes);

        for (int i = 0; i < chunks.size(); i++) {
            Chunker.Chunk chunk = chunks.get(i);
            String contentHash = sha256(chunk.getContent());

            // 去重：相同内容已存在则跳过
            if (existingHashes.contains(contentHash)) {
                duplicateChunks++;
                log.debug("Skipping duplicate chunk {} (hash={} already exists)", i, contentHash.substring(0, 8));
                continue;
            }

            float[] contentVector;
            try {
                contentVector = embedder.embed(chunk.getContent());
            } catch (Exception e) {
                log.warn("Failed to embed chunk {} (content length={}), skipping: {}", i, chunk.getContent().length(), e.getMessage());
                skippedChunks++;
                continue;
            }

            float[] questionVector = zeroVector;
            if ("qa".equals(chunk.getChunkType()) && chunk.getQuestion() != null) {
                try {
                    questionVector = embedder.embed(chunk.getQuestion());
                } catch (Exception e) {
                    log.warn("Failed to embed question for chunk {}, using zero vector: {}", i, e.getMessage());
                }
            }

            Map<String, Object> row = new HashMap<>();
            row.put("id", contentHash);
            row.put("content", chunk.getContent());
            row.put("vector", contentVector);
            row.put("question", chunk.getQuestion() != null ? chunk.getQuestion() : "");
            row.put("question_vector", questionVector);
            row.put("source", source != null ? source : "api");
            row.put("title", title != null ? title : "");
            row.put("chunk_index", chunk.getChunkIndex());
            row.put("content_hash", contentHash);
            row.put("created_at", now);
            row.put("ingest_time", now);
            row.put("hit_count", 0L);
            row.put("adopt_count", 0L);
            row.put("relevance_score", 1.0f);
            row.put("chunk_type", chunk.getChunkType());

            // v11 新增：溯源信息字段
            row.put("page_number", (long) chunk.getPageNumber());
            row.put("chapter", chunk.getChapter() != null ? chunk.getChapter() : "");
            row.put("context_pages", chunk.getContextPages() != null ? chunk.getContextPages() : "");
            row.put("table_html", chunk.getTableHtml() != null ? chunk.getTableHtml() : "");
            row.put("source_filename", chunk.getSourceFilename() != null ? chunk.getSourceFilename() : "");

            insertBatch.add(row);
            ingestedChunks++;

            if (insertBatch.size() >= ingestProperties.getMilvusBatchSize()) {
                insertBatch(collectionName, insertBatch);
                insertBatch.clear();
            }
        }

        if (!insertBatch.isEmpty()) {
            insertBatch(collectionName, insertBatch);
        }

        try {
            milvusManager.flush(collectionName);
        } catch (Exception e) {
            log.warn("Failed to flush collection {}: {}", collectionName, e.getMessage());
        }

        log.info("Ingestion complete: total={}, ingested={}, skipped(Embed失败)={}, duplicated={}",
                totalChunks, ingestedChunks, skippedChunks, duplicateChunks);
        return new IngestResponse(totalChunks, ingestedChunks, skippedChunks + duplicateChunks);
    }

    /**
     * 批量查询集合中已存在的 content_hash，用于去重
     */
    private Set<String> queryExistingHashes(String collectionName, Set<String> hashes) {
        if (hashes.isEmpty()) return Collections.emptySet();
        Set<String> existing = new HashSet<>();
        // 分批查询，每批100个hash，避免filter过长
        List<String> hashList = new ArrayList<>(hashes);
        int batchSize = 100;
        for (int start = 0; start < hashList.size(); start += batchSize) {
            List<String> batch = hashList.subList(start, Math.min(start + batchSize, hashList.size()));
            String filter = batch.stream()
                    .map(h -> "id == \"" + h + "\"")
                    .collect(Collectors.joining(" or "));
            try {
                QueryReq queryReq = QueryReq.builder()
                        .collectionName(collectionName)
                        .filter(filter)
                        .outputFields(List.of("id"))
                        .limit((long) batch.size())
                        .build();
                QueryResp resp = milvusManager.getClient().query(queryReq);
                for (QueryResp.QueryResult row : resp.getQueryResults()) {
                    existing.add(row.getEntity().getOrDefault("id", "").toString());
                }
            } catch (Exception e) {
                log.warn("Failed to query existing hashes for dedup: {}", e.getMessage());
            }
        }
        return existing;
    }

    private void insertBatch(String collectionName, List<Map<String, Object>> rows) {
        try {
            InsertReq insertReq = InsertReq.builder()
                    .collectionName(collectionName)
                    .data(MilvusDataConverter.toJsonObjectList(rows))
                    .build();
            milvusManager.getClient().insert(insertReq);
        } catch (Exception e) {
            log.error("Failed to insert batch into {}: {}", collectionName, e.getMessage());
            throw new RuntimeException("Failed to insert into Milvus: " + e.getMessage());
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
}
