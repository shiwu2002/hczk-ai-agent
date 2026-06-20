package com.hczk.hczkaiagentserver.knowledge.chunker;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Chunker {

    private final int chunkSize;
    private final int overlapSentences;

    // QA format patterns
    private static final Pattern QA_PATTERN = Pattern.compile(
            "(?:Q:|问:|问题:)\\s*(.+?)\\s*(?:A:|答:|答案:)\\s*(.+?)(?=(?:Q:|问:|问题:)|$)",
            Pattern.DOTALL
    );

    public Chunker(int chunkSize, int overlapSentences) {
        this.chunkSize = chunkSize;
        this.overlapSentences = overlapSentences;
    }

    @Data
    @Builder
    public static class Chunk {
        private String content;
        private String question;
        private String chunkType; // "qa" / "prose" / "table"
        private int chunkIndex;

        // ===== v11 新增：溯源信息 =====
        /** 页码（PDF 为实际页码，TXT/DOCX 为章节序号） */
        @Builder.Default
        private int pageNumber = 0;

        /** 所属章节标题 */
        @Builder.Default
        private String chapter = "";

        /** 关联的上下文页码列表（如跨页表格涉及的多页），格式 "1,2,3" */
        @Builder.Default
        private String contextPages = "";

        /** 表格的 Markdown/HTML 结构化表示（仅 chunkType=table 时有值） */
        @Builder.Default
        private String tableHtml = "";

        /** 源文件名 */
        @Builder.Default
        private String sourceFilename = "";
    }

    /**
     * 自动检测文本类型并分块
     */
    public List<Chunk> split(String text) {
        return split(text, "auto");
    }

    /**
     * 根据文档类型分块
     * @param text 文本内容
     * @param docType 文档类型: "auto"自动检测, "qa"强制问答, "prose"强制论文/散文
     */
    public List<Chunk> split(String text, String docType) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        if ("qa".equals(docType)) {
            // 强制 QA 模式：尝试 QA 分割，如果检测不到则将整段作为一个 QA 对
            List<Chunk> qaChunks = tryQASplit(text);
            if (qaChunks != null && !qaChunks.isEmpty()) {
                return qaChunks;
            }
            // 没有检测到 QA 格式，将整段文本作为一个 prose 块
            return List.of(Chunk.builder()
                    .content(text.trim())
                    .question(null)
                    .chunkType("prose")
                    .chunkIndex(0)
                    .build());
        }

        if ("prose".equals(docType)) {
            // 强制论文模式：直接按散文分块
            return proseSplit(text);
        }

        // auto 模式：先尝试 QA，检测不到则走 prose
        List<Chunk> qaChunks = tryQASplit(text);
        if (qaChunks != null && qaChunks.size() >= 2) {
            return qaChunks;
        }
        return proseSplit(text);
    }

    /**
     * 将预解析的 QA 对列表转为 Chunk 列表
     * 用于 Excel 等结构化数据，外部已将列映射为问答对
     */
    public List<Chunk> splitQAPairs(List<String[]> qaPairs) {
        List<Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < qaPairs.size(); i++) {
            String[] pair = qaPairs.get(i);
            String question = pair[0] != null ? pair[0].trim() : "";
            String answer = pair.length > 1 && pair[1] != null ? pair[1].trim() : "";

            if (answer.isEmpty()) continue;

            // 如果有额外内容（第3列起），追加到 answer
            if (pair.length > 2) {
                StringBuilder extra = new StringBuilder();
                for (int j = 2; j < pair.length; j++) {
                    if (pair[j] != null && !pair[j].isBlank()) {
                        extra.append("\n").append(pair[j].trim());
                    }
                }
                if (!extra.isEmpty()) {
                    answer += extra.toString();
                }
            }

            chunks.add(Chunk.builder()
                    .content(answer)
                    .question(question.isEmpty() ? null : question)
                    .chunkType(question.isEmpty() ? "prose" : "qa")
                    .chunkIndex(i)
                    .build());
        }
        return chunks;
    }

    private List<Chunk> tryQASplit(String text) {
        Matcher matcher = QA_PATTERN.matcher(text);
        List<Chunk> chunks = new ArrayList<>();
        int index = 0;
        while (matcher.find()) {
            String question = matcher.group(1).trim();
            String answer = matcher.group(2).trim();
            if (question.isEmpty() || answer.isEmpty()) continue;
            chunks.add(Chunk.builder()
                    .content(answer)
                    .question(question)
                    .chunkType("qa")
                    .chunkIndex(index++)
                    .build());
        }
        return chunks;
    }

    /**
     * Split prose text by sentences (Chinese/English punctuation aware).
     * Overlap N sentences between chunks for context continuity.
     */
    private List<Chunk> proseSplit(String text) {
        List<String> sentences = splitSentences(text);
        if (sentences.isEmpty()) {
            return List.of();
        }

        List<Chunk> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        List<String> currentSentences = new ArrayList<>();
        int chunkIndex = 0;

        for (String sentence : sentences) {
            currentSentences.add(sentence);
            currentChunk.append(sentence);

            if (currentChunk.length() >= chunkSize) {
                chunks.add(Chunk.builder()
                        .content(currentChunk.toString().trim())
                        .question(null)
                        .chunkType("prose")
                        .chunkIndex(chunkIndex++)
                        .build());

                int overlapStart = Math.max(0, currentSentences.size() - overlapSentences);
                List<String> overlapSentencesList = currentSentences.subList(overlapStart, currentSentences.size());
                currentSentences = new ArrayList<>(overlapSentencesList);
                currentChunk = new StringBuilder();
                for (String s : overlapSentencesList) {
                    currentChunk.append(s);
                }
            }
        }

        if (currentChunk.length() > 0 && currentChunk.toString().trim().length() > 0) {
            chunks.add(Chunk.builder()
                    .content(currentChunk.toString().trim())
                    .question(null)
                    .chunkType("prose")
                    .chunkIndex(chunkIndex)
                    .build());
        }

        return chunks;
    }

    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        Pattern pattern = Pattern.compile("([^。！？.!?\\n]+[。！？.!?]?\\n?)");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String s = matcher.group().trim();
            if (!s.isEmpty()) {
                sentences.add(s);
            }
        }
        if (sentences.isEmpty() && !text.isBlank()) {
            sentences.add(text);
        }
        return sentences;
    }
}
