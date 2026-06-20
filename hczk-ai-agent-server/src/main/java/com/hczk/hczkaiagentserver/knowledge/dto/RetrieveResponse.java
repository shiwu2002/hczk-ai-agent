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

        // ===== v11 新增：溯源信息 =====
        /** 页码（PDF 为实际页码，TXT/DOCX 为章节序号，0 表示未知） */
        private int pageNumber;

        /** 所属章节标题 */
        private String chapter;

        /** 关联的上下文页码列表，格式 "1,2,3"（如跨页表格涉及的多页） */
        private String contextPages;

        /** 表格的 Markdown/HTML 结构化表示（仅 chunkType=table 时有值） */
        private String tableHtml;

        /** 源文件名 */
        private String sourceFilename;
    }
}
