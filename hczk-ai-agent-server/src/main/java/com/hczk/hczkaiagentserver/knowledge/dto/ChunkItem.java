package com.hczk.hczkaiagentserver.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkItem {
    private String id;
    private String content;
    private String source;
    private String title;
    private int chunkIndex;
    private String question;
    private String contentHash;
    private long createdAt;
    private long ingestTime;
    private long hitCount;
    private long adoptCount;
    private double relevanceScore;
    private String chunkType;
}
