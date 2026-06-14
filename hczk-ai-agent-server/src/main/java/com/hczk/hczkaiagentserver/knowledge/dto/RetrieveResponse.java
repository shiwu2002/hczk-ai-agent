package com.hczk.hczkaiagentserver.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrieveResponse {
    private List<RetrieveResult> results;
    private String strategy;
    private double confidence;
    private boolean fallbackUsed;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrieveResult {
        private String content;
        private double score;
        private ChunkMetadata metadata;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkMetadata {
        private String source;
        private String title;
        private int chunkIndex;
        private String question;
        private String matchType;
        private String chunkId;
        private long hitCount;
        private long adoptCount;
        private double relevanceScore;
        private String chunkType;
    }
}
