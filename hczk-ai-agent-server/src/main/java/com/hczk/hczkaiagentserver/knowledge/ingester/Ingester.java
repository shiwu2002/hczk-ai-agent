package com.hczk.hczkaiagentserver.knowledge.ingester;

import com.hczk.hczkaiagentserver.knowledge.chunker.Chunker;
import com.hczk.hczkaiagentserver.knowledge.config.EmbeddingProperties;
import com.hczk.hczkaiagentserver.knowledge.config.IngestProperties;
import com.hczk.hczkaiagentserver.knowledge.dto.IngestJsonRequest;
import com.hczk.hczkaiagentserver.knowledge.dto.IngestResponse;
import com.hczk.hczkaiagentserver.knowledge.embedder.Embedder;
import com.hczk.hczkaiagentserver.knowledge.milvus.MilvusManager;
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

    public IngestResponse ingestText(String agentId, String collectionName, String text,
                                      String source, String title) {
        return ingestText(agentId, collectionName, text, source, title, "auto");
    }

    public IngestResponse ingestText(String agentId, String collectionName, String text,
                                      String source, String title, String docType) {
        String fullCollectionName = milvusManager.buildCollectionName(agentId, collectionName);
        milvusManager.ensureCollection(fullCollectionName);
        Chunker chunker = new Chunker(ingestProperties.getChunkSize(), ingestProperties.getChunkOverlapSentences());
        List<Chunker.Chunk> chunks = chunker.split(text, docType);
        return ingestChunks(fullCollectionName, chunks, source, title);
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
            List<Chunker.Chunk> chunks = chunker.split(item.getContent());
            allChunks.addAll(chunks);
        }
        String source = data.get(0).getSource();
        String title = data.get(0).getTitle();
        return ingestChunks(fullCollectionName, allChunks, source, title);
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
