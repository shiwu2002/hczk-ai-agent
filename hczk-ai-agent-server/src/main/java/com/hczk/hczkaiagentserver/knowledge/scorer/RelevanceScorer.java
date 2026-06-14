package com.hczk.hczkaiagentserver.knowledge.scorer;

import com.hczk.hczkaiagentserver.knowledge.config.RelevanceProperties;
import com.hczk.hczkaiagentserver.knowledge.milvus.MilvusManager;
import com.hczk.hczkaiagentserver.knowledge.util.MilvusDataConverter;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.QueryResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelevanceScorer {

    private final MilvusManager milvusManager;
    private final RelevanceProperties relevanceProperties;

    public double calculateScore(long ingestTime, long hitCount, long adoptCount) {
        long ageMs = System.currentTimeMillis() - ingestTime;
        double ageFactor = Math.pow(0.5, (double) ageMs / relevanceProperties.getHalfLifeMs());
        double adoptRatio = adoptCount / Math.max(hitCount, 1.0);
        double adoptBonus = Math.log(1 + adoptCount);
        double score = ageFactor * (1 + adoptRatio * relevanceProperties.getAdoptRatioWeight()
                + adoptBonus * relevanceProperties.getAdoptBonusWeight());
        return clamp(score);
    }

    public int recalculateForCollection(String collectionName) {
        int updated = 0;
        try {
            QueryReq queryReq = QueryReq.builder()
                    .collectionName(collectionName)
                    .filter("id != \"\"")
                    .limit(16384L)
                    .outputFields(List.of("id", "content", "vector", "question", "question_vector",
                            "source", "title", "chunk_index", "content_hash", "created_at",
                            "ingest_time", "hit_count", "adopt_count", "relevance_score", "chunk_type"))
                    .build();

            QueryResp queryResp = milvusManager.getClient().query(queryReq);

            for (QueryResp.QueryResult row : queryResp.getQueryResults()) {
                Map<String, Object> entity = new HashMap<>(row.getEntity());
                long ingestTime = entity.containsKey("ingest_time") ? ((Number) entity.get("ingest_time")).longValue() : 0L;
                long hitCount = entity.containsKey("hit_count") ? ((Number) entity.get("hit_count")).longValue() : 0L;
                long adoptCount = entity.containsKey("adopt_count") ? ((Number) entity.get("adopt_count")).longValue() : 0L;
                double oldScore = entity.containsKey("relevance_score") ? ((Number) entity.get("relevance_score")).doubleValue() : relevanceProperties.getDefaultScore();
                double newScore = calculateScore(ingestTime, hitCount, adoptCount);

                if (Math.abs(newScore - oldScore) > relevanceProperties.getUpdateThreshold()) {
                    entity.put("relevance_score", newScore);
                    try {
                        milvusManager.getClient().upsert(UpsertReq.builder()
                                .collectionName(collectionName)
                                .data(List.of(MilvusDataConverter.toJsonObject(entity)))
                                .build());
                        updated++;
                    } catch (Exception e) {
                        log.warn("Failed to update relevance score for chunk {}: {}", entity.get("id"), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to recalculate scores for {}: {}", collectionName, e.getMessage());
        }
        return updated;
    }

    public int recalculateForAgent(String agentId) {
        List<String> collections = milvusManager.listCollections(agentId);
        int totalUpdated = 0;
        for (String collection : collections) {
            totalUpdated += recalculateForCollection(collection);
        }
        log.info("Recalculated scores for agent {}: {} chunks updated", agentId, totalUpdated);
        return totalUpdated;
    }

    public void markAdopted(String collectionName, String chunkId) {
        try {
            QueryReq queryReq = QueryReq.builder()
                    .collectionName(collectionName)
                    .filter("id == \"" + chunkId.replace("\"", "\\\"") + "\"")
                    .outputFields(List.of("id", "content", "vector", "question", "question_vector",
                            "source", "title", "chunk_index", "content_hash", "created_at",
                            "ingest_time", "hit_count", "adopt_count", "relevance_score", "chunk_type"))
                    .build();

            QueryResp queryResp = milvusManager.getClient().query(queryReq);
            if (!queryResp.getQueryResults().isEmpty()) {
                Map<String, Object> entity = new HashMap<>(queryResp.getQueryResults().get(0).getEntity());
                long adoptCount = entity.containsKey("adopt_count") ? ((Number) entity.get("adopt_count")).longValue() + 1 : 1L;
                entity.put("adopt_count", adoptCount);
                long ingestTime = entity.containsKey("ingest_time") ? ((Number) entity.get("ingest_time")).longValue() : 0L;
                long hitCount = entity.containsKey("hit_count") ? ((Number) entity.get("hit_count")).longValue() : 0L;
                double newScore = calculateScore(ingestTime, hitCount, adoptCount);
                entity.put("relevance_score", newScore);

                milvusManager.getClient().upsert(UpsertReq.builder()
                        .collectionName(collectionName)
                        .data(List.of(MilvusDataConverter.toJsonObject(entity)))
                        .build());
            }
        } catch (Exception e) {
            log.error("Failed to mark chunk {} as adopted: {}", chunkId, e.getMessage());
            throw new RuntimeException("Failed to mark chunk as adopted: " + e.getMessage());
        }
    }

    private double clamp(double score) {
        return Math.max(relevanceProperties.getMinScore(), Math.min(relevanceProperties.getMaxScore(), score));
    }
}
