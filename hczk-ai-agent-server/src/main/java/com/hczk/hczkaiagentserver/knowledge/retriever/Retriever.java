package com.hczk.hczkaiagentserver.knowledge.retriever;

import com.hczk.hczkaiagentserver.entity.RetrievalLog;
import com.hczk.hczkaiagentserver.knowledge.config.EmbeddingProperties;
import com.hczk.hczkaiagentserver.knowledge.config.RetrieveProperties;
import com.hczk.hczkaiagentserver.knowledge.dto.RetrieveResponse;
import com.hczk.hczkaiagentserver.knowledge.embedder.Embedder;
import com.hczk.hczkaiagentserver.knowledge.milvus.MilvusManager;
import com.hczk.hczkaiagentserver.knowledge.util.MilvusDataConverter;
import com.hczk.hczkaiagentserver.mapper.RetrievalLogMapper;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class Retriever {

    private final MilvusManager milvusManager;
    private final Embedder embedder;
    private final RetrieveProperties retrieveProperties;
    private final EmbeddingProperties embeddingProperties;
    private final RetrievalLogMapper retrievalLogMapper;

    private static final Pattern KEYWORD_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]+|[a-zA-Z]+");

    private static final Map<String, String> FALLBACK_FAQ = Map.of(
            "人工", "如需转接人工客服，请拨打客服热线或回复'转人工'。",
            "退款", "退款申请将在1-3个工作日内处理，请耐心等待。",
            "投诉", "非常抱歉给您带来不便，我们会尽快处理您的投诉。"
    );

    private static final String DEFAULT_FALLBACK = "抱歉，我暂时无法回答您的问题，请稍后再试或联系人工客服。";

    public RetrieveResponse retrieve(String agentId, String collectionName, String query, Integer topK) {
        String fullCollectionName = milvusManager.buildCollectionName(agentId, collectionName);
        int k = topK != null ? Math.min(topK, retrieveProperties.getMaxTopK()) : retrieveProperties.getDefaultTopK();
        long startTime = System.currentTimeMillis();

        try {
            milvusManager.ensureCollection(fullCollectionName);
        } catch (Exception e) {
            RetrieveResponse resp = buildFallbackResponse(query, k);
            asyncRecordRetrievalLog(agentId, collectionName, query, k, resp, System.currentTimeMillis() - startTime);
            return resp;
        }

        try {
            RetrieveResponse response = hybridSearch(fullCollectionName, query, k);
            if (response != null && !response.getResults().isEmpty()) {
                asyncRecordHit(fullCollectionName, response.getResults());
                asyncRecordRetrievalLog(agentId, collectionName, query, k, response, System.currentTimeMillis() - startTime);
                return response;
            }
        } catch (Exception e) {
            log.warn("Hybrid search failed: {}", e.getMessage());
        }

        try {
            RetrieveResponse response = vectorSearch(fullCollectionName, query, k);
            if (response != null && !response.getResults().isEmpty()) {
                asyncRecordHit(fullCollectionName, response.getResults());
                asyncRecordRetrievalLog(agentId, collectionName, query, k, response, System.currentTimeMillis() - startTime);
                return response;
            }
        } catch (Exception e) {
            log.warn("Vector search failed: {}", e.getMessage());
        }

        try {
            RetrieveResponse response = keywordSearch(fullCollectionName, query, k);
            if (response != null && !response.getResults().isEmpty()) {
                asyncRecordHit(fullCollectionName, response.getResults());
                asyncRecordRetrievalLog(agentId, collectionName, query, k, response, System.currentTimeMillis() - startTime);
                return response;
            }
        } catch (Exception e) {
            log.warn("Keyword search failed: {}", e.getMessage());
        }

        RetrieveResponse fallbackResp = buildFallbackResponse(query, k);
        asyncRecordRetrievalLog(agentId, collectionName, query, k, fallbackResp, System.currentTimeMillis() - startTime);
        return fallbackResp;
    }

    private RetrieveResponse hybridSearch(String collectionName, String query, int topK) {
        float[] queryVector = embedder.embed(query);
        SearchResp contentResults = searchVectors(collectionName, queryVector, "vector", topK);
        SearchResp questionResults = searchVectors(collectionName, queryVector, "question_vector", topK);

        Map<String, RetrieveResponse.RetrieveResult> merged = new LinkedHashMap<>();

        // content 向量匹配
        for (SearchResp.SearchResult result : contentResults.getSearchResults().get(0)) {
            String id = result.getId().toString();
            double score = result.getScore().doubleValue();
            Map<String, Object> entity = result.getEntity();
            double relevanceScore = entity.containsKey("relevance_score") ?
                    ((Number) entity.get("relevance_score")).doubleValue() : 1.0;
            double adjustedScore = score * normalizeRelevance(relevanceScore);
            merged.put(id, buildResult(id, score, adjustedScore, entity, "content"));
        }

        // question 向量匹配
        for (SearchResp.SearchResult result : questionResults.getSearchResults().get(0)) {
            String id = result.getId().toString();
            double rawScore = result.getScore().doubleValue();
            Map<String, Object> entity = result.getEntity();
            double relevanceScore = entity.containsKey("relevance_score") ?
                    ((Number) entity.get("relevance_score")).doubleValue() : 1.0;
            String chunkType = entity.getOrDefault("chunk_type", "prose").toString();

            // QA 类型：question_vector 是有效向量，直接用原始相似度（不加权重放大）
            //   原因：中文问题句式相似（都是"XX是什么/为什么"），embedding 原始分偏高，
            //         盲目乘以 questionWeight 会导致不相关的 QA 也拿到高分
            // 非 QA 类型：question_vector 可能是零向量，大幅降权避免噪声
            double adjustedScore;
            if ("qa".equals(chunkType)) {
                adjustedScore = Math.min(rawScore * normalizeRelevance(relevanceScore), 1.0);
            } else {
                adjustedScore = rawScore * 0.2 * normalizeRelevance(relevanceScore);
            }

            RetrieveResponse.RetrieveResult existing = merged.get(id);
            if (existing == null || adjustedScore > existing.getScore()) {
                merged.put(id, buildResult(id, rawScore, adjustedScore, entity, "question"));
            }
        }

        List<RetrieveResponse.RetrieveResult> results = merged.values().stream()
                .sorted(Comparator.comparingDouble(RetrieveResponse.RetrieveResult::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
        return new RetrieveResponse(results, "hybrid", retrieveProperties.getHybridConfidence(), false);
    }

    private RetrieveResponse vectorSearch(String collectionName, String query, int topK) {
        float[] queryVector = embedder.embed(query);
        int expandedTopK = topK * 3;
        SearchResp contentResults = searchVectors(collectionName, queryVector, "vector", expandedTopK);
        SearchResp questionResults = searchVectors(collectionName, queryVector, "question_vector", expandedTopK);

        Map<String, RetrieveResponse.RetrieveResult> merged = new LinkedHashMap<>();

        for (SearchResp.SearchResult result : contentResults.getSearchResults().get(0)) {
            String id = result.getId().toString();
            double score = result.getScore().doubleValue();
            Map<String, Object> entity = result.getEntity();
            double relevanceScore = entity.containsKey("relevance_score") ?
                    ((Number) entity.get("relevance_score")).doubleValue() : 1.0;
            double adjustedScore = score * normalizeRelevance(relevanceScore);
            merged.put(id, buildResult(id, score, adjustedScore, entity, "content"));
        }

        // 合并 question_vector 结果
        for (SearchResp.SearchResult result : questionResults.getSearchResults().get(0)) {
            String id = result.getId().toString();
            double rawScore = result.getScore().doubleValue();
            Map<String, Object> entity = result.getEntity();
            double relevanceScore = entity.containsKey("relevance_score") ?
                    ((Number) entity.get("relevance_score")).doubleValue() : 1.0;
            String chunkType = entity.getOrDefault("chunk_type", "prose").toString();

            double adjustedScore;
            if ("qa".equals(chunkType)) {
                adjustedScore = Math.min(rawScore * normalizeRelevance(relevanceScore), 1.0);
            } else {
                adjustedScore = rawScore * 0.2 * normalizeRelevance(relevanceScore);
            }

            RetrieveResponse.RetrieveResult existing = merged.get(id);
            if (existing == null || adjustedScore > existing.getScore()) {
                merged.put(id, buildResult(id, rawScore, adjustedScore, entity, "question"));
            }
        }

        List<RetrieveResponse.RetrieveResult> results = merged.values().stream()
                .sorted(Comparator.comparingDouble(RetrieveResponse.RetrieveResult::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
        return new RetrieveResponse(results, "vector", retrieveProperties.getVectorConfidence(), false);
    }

    private RetrieveResponse keywordSearch(String collectionName, String query, int topK) {
        List<String> keywords = extractKeywords(query);
        if (keywords.isEmpty()) {
            return null;
        }

        // 同时搜索 content 和 question 字段
        String kwFilter = keywords.stream()
                .limit(5)
                .map(kw -> "content like \"%" + escapeFilter(kw) + "%\" or question like \"%" + escapeFilter(kw) + "%\"")
                .collect(Collectors.joining(" or "));

        QueryReq queryReq = QueryReq.builder()
                .collectionName(collectionName)
                .filter(kwFilter)
                .limit((long) topK)
                .outputFields(List.of("id", "content", "source", "title", "chunk_index",
                        "question", "content_hash", "created_at", "ingest_time",
                        "hit_count", "adopt_count", "relevance_score", "chunk_type",
                        // v11 新增：溯源字段
                        "page_number", "chapter", "context_pages", "table_html", "source_filename"))
                .build();

        QueryResp queryResp = milvusManager.getClient().query(queryReq);
        List<RetrieveResponse.RetrieveResult> results = new ArrayList<>();
        for (QueryResp.QueryResult row : queryResp.getQueryResults()) {
            Map<String, Object> entity = new HashMap<>(row.getEntity());
            String id = entity.get("id").toString();
            double relevanceScore = entity.containsKey("relevance_score") ?
                    ((Number) entity.get("relevance_score")).doubleValue() : 1.0;
            results.add(buildResult(id, 0.5, 0.5 * normalizeRelevance(relevanceScore), entity, "keyword"));
        }
        return new RetrieveResponse(results, "keyword", retrieveProperties.getKeywordConfidence(), false);
    }

    private RetrieveResponse buildFallbackResponse(String query, int topK) {
        List<RetrieveResponse.RetrieveResult> results = new ArrayList<>();
        for (Map.Entry<String, String> entry : FALLBACK_FAQ.entrySet()) {
            if (query.contains(entry.getKey())) {
                RetrieveResponse.ChunkMetadata metadata = new RetrieveResponse.ChunkMetadata();
                metadata.setSource("fallback");
                metadata.setChunkType("fallback");
                metadata.setMatchType("keyword");
                results.add(new RetrieveResponse.RetrieveResult(
                        entry.getValue(), retrieveProperties.getFallbackConfidence(), metadata));
                break;
            }
        }
        if (results.isEmpty()) {
            RetrieveResponse.ChunkMetadata metadata = new RetrieveResponse.ChunkMetadata();
            metadata.setSource("fallback");
            metadata.setChunkType("fallback");
            metadata.setMatchType("default");
            results.add(new RetrieveResponse.RetrieveResult(
                    DEFAULT_FALLBACK, retrieveProperties.getFallbackConfidence(), metadata));
        }
        return new RetrieveResponse(results, "fallback", retrieveProperties.getFallbackConfidence(), true);
    }

    private SearchResp searchVectors(String collectionName, float[] vector, String vectorField, int topK) {
        SearchReq searchReq = SearchReq.builder()
                .collectionName(collectionName)
                .data(List.of(new FloatVec(vector)))
                .annsField(vectorField)
                .topK(topK)
                .searchParams(Map.of("nprobe", retrieveProperties.getNprobe()))
                .outputFields(List.of("id", "content", "source", "title", "chunk_index",
                        "question", "content_hash", "created_at", "ingest_time",
                        "hit_count", "adopt_count", "relevance_score", "chunk_type",
                        // v11 新增：溯源字段
                        "page_number", "chapter", "context_pages", "table_html", "source_filename"))
                .build();
        return milvusManager.getClient().search(searchReq);
    }

    private RetrieveResponse.RetrieveResult buildResult(String id, double rawScore, double adjustedScore,
                                                         Map<String, Object> entity, String matchType) {
        RetrieveResponse.ChunkMetadata metadata = new RetrieveResponse.ChunkMetadata();
        metadata.setSource(entity.getOrDefault("source", "").toString());
        metadata.setTitle(entity.getOrDefault("title", "").toString());
        metadata.setChunkIndex(entity.containsKey("chunk_index") ? ((Number) entity.get("chunk_index")).intValue() : 0);
        metadata.setQuestion(entity.getOrDefault("question", "").toString());
        metadata.setMatchType(matchType);
        metadata.setChunkId(id);
        metadata.setHitCount(entity.containsKey("hit_count") ? ((Number) entity.get("hit_count")).longValue() : 0);
        metadata.setAdoptCount(entity.containsKey("adopt_count") ? ((Number) entity.get("adopt_count")).longValue() : 0);
        metadata.setRelevanceScore(entity.containsKey("relevance_score") ? ((Number) entity.get("relevance_score")).doubleValue() : 1.0);
        metadata.setChunkType(entity.getOrDefault("chunk_type", "prose").toString());

        // v11 新增：溯源信息
        metadata.setPageNumber(entity.containsKey("page_number") ? ((Number) entity.get("page_number")).intValue() : 0);
        metadata.setChapter(entity.getOrDefault("chapter", "").toString());
        metadata.setContextPages(entity.getOrDefault("context_pages", "").toString());
        metadata.setTableHtml(entity.getOrDefault("table_html", "").toString());
        metadata.setSourceFilename(entity.getOrDefault("source_filename", "").toString());

        return new RetrieveResponse.RetrieveResult(
                entity.getOrDefault("content", "").toString(), adjustedScore, metadata);
    }

    /**
     * 归一化相关性评分
     * relevance_score=1.0（默认/正常）→ 返回 1.0（不惩罚）
     * relevance_score>1.0（优质）→ 放大，最高 2.0x
     * relevance_score<1.0（低质）→ 惩罚，最低 0.2x
     */
    private double normalizeRelevance(double score) {
        if (score >= 1.0) {
            // 正常或优质数据：1.0 → 1.0，10.0 → 2.0
            double clamped = Math.min(score, 10.0);
            return 1.0 + (clamped - 1.0) / 9.0;
        } else {
            // 低质量数据：1.0 → 1.0，0.01 → 0.2
            double clamped = Math.max(score, 0.01);
            return 0.2 + (clamped - 0.01) / 0.99 * 0.8;
        }
    }

    private List<String> extractKeywords(String query) {
        Matcher matcher = KEYWORD_PATTERN.matcher(query);
        Set<String> keywords = new LinkedHashSet<>();
        while (matcher.find() && keywords.size() < 5) {
            String kw = matcher.group().trim();
            if (kw.length() >= 2) {
                keywords.add(kw);
            }
        }
        return new ArrayList<>(keywords);
    }

    private String escapeFilter(String value) {
        return value.replace("\"", "\\\"");
    }

    /**
     * 异步记录检索日志到数据库，用于命中率监控和检索质量分析
     */
    @Async
    public void asyncRecordRetrievalLog(String agentId, String collectionName, String query,
                                         int topK, RetrieveResponse response, long durationMs) {
        try {
            RetrievalLog logEntry = new RetrievalLog();
            logEntry.setAgentId(agentId);
            logEntry.setCollectionName(collectionName);
            logEntry.setQuery(query != null && query.length() > 1000 ? query.substring(0, 1000) : query);
            logEntry.setStrategy(response.getStrategy());
            logEntry.setTopK(topK);
            logEntry.setConfidence(response.getConfidence());
            logEntry.setFallbackUsed(response.isFallbackUsed());
            logEntry.setDurationMs(durationMs);

            List<RetrieveResponse.RetrieveResult> results = response.getResults();
            boolean hitSuccess = results != null && !results.isEmpty() && !response.isFallbackUsed();
            logEntry.setHitSuccess(hitSuccess);
            logEntry.setHitCount(results != null ? results.size() : 0);

            if (results != null && !results.isEmpty()) {
                logEntry.setTopScore(results.get(0).getScore());
                // 构建命中块溯源摘要 JSON
                logEntry.setHitSources(buildHitSourcesJson(results));
            } else {
                logEntry.setTopScore(0.0);
                logEntry.setHitSources("[]");
            }

            retrievalLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.debug("Failed to record retrieval log: {}", e.getMessage());
        }
    }

    /**
     * 构建命中块溯源摘要 JSON（页码、章节、源文件、得分）
     */
    private String buildHitSourcesJson(List<RetrieveResponse.RetrieveResult> results) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < results.size() && i < 10; i++) {
            RetrieveResponse.RetrieveResult r = results.get(i);
            RetrieveResponse.ChunkMetadata m = r.getMetadata();
            if (i > 0) sb.append(",");
            sb.append("{");
            sb.append("\"score\":").append(r.getScore()).append(",");
            sb.append("\"page\":").append(m != null ? m.getPageNumber() : 0).append(",");
            sb.append("\"chapter\":\"").append(escapeJson(m != null ? m.getChapter() : "")).append("\",");
            sb.append("\"source\":\"").append(escapeJson(m != null ? m.getSourceFilename() : "")).append("\",");
            sb.append("\"chunkType\":\"").append(escapeJson(m != null ? m.getChunkType() : "")).append("\"");
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    @Async
    public void asyncRecordHit(String collectionName, List<RetrieveResponse.RetrieveResult> results) {
        for (RetrieveResponse.RetrieveResult result : results) {
            if (result.getMetadata() != null && result.getMetadata().getChunkId() != null) {
                try {
                    String chunkId = result.getMetadata().getChunkId();
                    QueryReq queryReq = QueryReq.builder()
                            .collectionName(collectionName)
                            .filter("id == \"" + escapeFilter(chunkId) + "\"")
                            .outputFields(List.of("id", "content", "vector", "question", "question_vector",
                                    "source", "title", "chunk_index", "content_hash", "created_at",
                                    "ingest_time", "hit_count", "adopt_count", "relevance_score", "chunk_type",
                                    // v11 新增：溯源字段
                                    "page_number", "chapter", "context_pages", "table_html", "source_filename"))
                            .build();
                    QueryResp queryResp = milvusManager.getClient().query(queryReq);
                    if (!queryResp.getQueryResults().isEmpty()) {
                        Map<String, Object> row = new HashMap<>(queryResp.getQueryResults().get(0).getEntity());
                        long hitCount = row.containsKey("hit_count") ? ((Number) row.get("hit_count")).longValue() + 1 : 1L;
                        row.put("hit_count", hitCount);
                        milvusManager.getClient().upsert(UpsertReq.builder()
                                .collectionName(collectionName)
                                .data(List.of(MilvusDataConverter.toJsonObject(row)))
                                .build());
                    }
                } catch (Exception e) {
                    log.debug("Failed to record hit for chunk {}: {}", result.getMetadata().getChunkId(), e.getMessage());
                }
            }
        }
    }
}
