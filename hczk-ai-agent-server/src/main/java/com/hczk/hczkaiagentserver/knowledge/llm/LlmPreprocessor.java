package com.hczk.hczkaiagentserver.knowledge.llm;

import com.hczk.hczkaiagentserver.entity.AiModel;
import com.hczk.hczkaiagentserver.knowledge.config.LlmPreprocessProperties;
import com.hczk.hczkaiagentserver.knowledge.parser.DocumentParser;
import com.hczk.hczkaiagentserver.service.AiModelService;
import com.hczk.hczkaiagentserver.service.ChatModelFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库入库 LLM 预处理器
 * 在文档入库前，通过 LLM 对文档进行一轮总结与语义切割，产出语义完整的知识块列表
 *
 * v11 增强：
 * - 感知篇章上下文（页码、章节），LLM 处理时能看到文档结构
 * - 跨页表格合并：将跨页的表格内容合并为一个完整块送入 LLM
 * - 返回带溯源信息的 SemanticChunk（页码、章节、上下文页）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmPreprocessor {

    private final LlmPreprocessProperties properties;
    private final ChatModelFactory chatModelFactory;
    private final AiModelService aiModelService;

    /**
     * 判断给定文本是否需要 LLM 预处理
     */
    public boolean shouldPreprocess(String text, String docType) {
        if (!properties.isEnabled()) return false;
        if (text == null || text.length() < properties.getMinTextLength()) return false;
        if ("qa".equals(docType)) return false;
        return true;
    }

    /**
     * 判断给定页列表是否需要 LLM 预处理
     */
    public boolean shouldPreprocessPages(List<DocumentParser.ParsedPage> pages, String docType) {
        if (!properties.isEnabled()) return false;
        if (pages == null || pages.isEmpty()) return false;
        if ("qa".equals(docType)) return false;
        int totalLen = pages.stream().mapToInt(p -> p.text() != null ? p.text().length() : 0).sum();
        return totalLen >= properties.getMinTextLength();
    }

    /**
     * 对文档进行 LLM 总结与语义切割（旧接口，纯文本模式）
     */
    public List<String> summarizeAndSplit(String text) {
        if (text == null || text.isBlank()) return List.of();

        int maxInput = properties.getMaxInputChars();
        if (text.length() <= maxInput) {
            return callLlmAndParse(text);
        }

        List<String> segments = splitForLlm(text, maxInput);
        List<String> allChunks = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            log.info("LLM 预处理分段 {}/{}，长度={} 字符", i + 1, segments.size(), segments.get(i).length());
            List<String> chunks = callLlmAndParse(segments.get(i));
            allChunks.addAll(chunks);
        }
        log.info("LLM 预处理完成：原始 {} 字符 → {} 个语义块", text.length(), allChunks.size());
        return allChunks;
    }

    /**
     * v11 新增：对带页码和章节信息的文档进行 LLM 总结与语义切割
     * 感知篇章上下文，跨页表格合并，返回带溯源信息的语义块
     *
     * @param pages 解析后的页面列表
     * @return 带溯源信息的语义块列表
     */
    public List<SemanticChunk> summarizeAndSplitWithPages(List<DocumentParser.ParsedPage> pages) {
        if (pages == null || pages.isEmpty()) return List.of();

        // 第一步：合并跨页表格
        List<MergedSegment> mergedSegments = mergeCrossPageTables(pages);

        List<SemanticChunk> allChunks = new ArrayList<>();
        int maxInput = properties.getMaxInputChars();

        for (MergedSegment segment : mergedSegments) {
            // 若内容过长，分段处理
            if (segment.text.length() > maxInput) {
                List<String> subSegments = splitForLlm(segment.text, maxInput);
                for (int i = 0; i < subSegments.size(); i++) {
                    log.info("LLM 预处理分段 {}/{}（{} 第 {} 段），长度={} 字符",
                            i + 1, subSegments.size(), segment.chapter, segment.pageNumbers, subSegments.get(i).length());
                    List<SemanticChunk> chunks = callLlmAndParseWithContext(
                            subSegments.get(i), segment.chapter, segment.pageNumbers, segment.sourceFilename);
                    allChunks.addAll(chunks);
                }
            } else {
                List<SemanticChunk> chunks = callLlmAndParseWithContext(
                        segment.text, segment.chapter, segment.pageNumbers, segment.sourceFilename);
                allChunks.addAll(chunks);
            }
        }

        log.info("LLM 预处理完成（页感知模式）：{} 页 → {} 个语义块", pages.size(), allChunks.size());
        return allChunks;
    }

    /**
     * 合并跨页表格：将连续的跨页表格页合并为一个段
     */
    private List<MergedSegment> mergeCrossPageTables(List<DocumentParser.ParsedPage> pages) {
        List<MergedSegment> segments = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        String currentChapter = "";
        List<Integer> currentPageNumbers = new ArrayList<>();
        String currentSource = "";

        for (DocumentParser.ParsedPage page : pages) {
            if (page.isTableContinuation() && currentText.length() > 0) {
                // 跨页表格延续：合并到当前段
                currentText.append("\n").append(page.text());
                currentPageNumbers.add(page.pageNumber());
                log.debug("合并跨页表格: 页 {} → 当前段包含页 {}", page.pageNumber(), currentPageNumbers);
            } else {
                // 保存前一段
                if (currentText.length() > 0) {
                    segments.add(new MergedSegment(
                            currentText.toString().trim(),
                            currentChapter,
                            new ArrayList<>(currentPageNumbers),
                            currentSource
                    ));
                }
                // 开始新段
                currentText = new StringBuilder(page.text());
                currentChapter = page.chapter() != null ? page.chapter() : "";
                currentPageNumbers = new ArrayList<>();
                currentPageNumbers.add(page.pageNumber());
                currentSource = page.sourceFilename() != null ? page.sourceFilename() : "";
            }
        }

        // 最后一段
        if (currentText.length() > 0) {
            segments.add(new MergedSegment(
                    currentText.toString().trim(),
                    currentChapter,
                    new ArrayList<>(currentPageNumbers),
                    currentSource
            ));
        }

        log.info("跨页表格合并完成：{} 页 → {} 个段", pages.size(), segments.size());
        return segments;
    }

    /**
     * 构建带页码和章节上下文的 LLM prompt
     */
    private String buildPageAwarePrompt(MergedSegment segment) {
        return """
                你是一个专业的知识库文档预处理助手。请阅读以下文档内容，进行总结与语义切割。

                文档上下文信息：
                - 章节：{chapter}
                - 页码：{pages}
                - 源文件：{source}

                要求：
                1. 理解文档的整体主题和结构，结合章节上下文进行语义切割
                2. 按语义将内容切分为多个独立、完整的知识块，每个块应能独立表达一个完整的知识点或事实
                3. 对冗长内容进行精炼总结，保留关键信息、数据、结论，去除口语化表达和重复内容
                4. 如果内容包含表格，请完整保留表格数据，不要截断或遗漏行
                5. 每个知识块目标长度约 {target} 字符，不要生硬截断句子
                6. 保持原文的专业术语和准确性，不要编造信息
                7. 各知识块之间用 ===CHUNK=== 分隔（前后各一个空行）
                8. 直接输出切割后的知识块，不要输出任何解释、前言、编号

                文档内容：
                ---
                {text}
                ---

                请输出切割后的知识块：
                """;
    }

    /**
     * 调用 LLM 并解析输出为带溯源信息的语义块列表
     */
    private List<SemanticChunk> callLlmAndParseWithContext(String text, String chapter,
                                                            List<Integer> pageNumbers, String sourceFilename) {
        ChatModel chatModel = getChatModel();

        String pageStr = pageNumbers.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        String promptText = buildPageAwarePrompt(new MergedSegment(text, chapter, pageNumbers, sourceFilename))
                .replace("{chapter}", chapter != null ? chapter : "")
                .replace("{pages}", pageStr)
                .replace("{source}", sourceFilename != null ? sourceFilename : "")
                .replace("{text}", text)
                .replace("{target}", String.valueOf(properties.getTargetChunkSize()));

        Prompt prompt = new Prompt(new UserMessage(promptText));
        ChatResponse response = chatModel.call(prompt);

        if (response == null || response.getResult() == null
                || response.getResult().getOutput() == null
                || response.getResult().getOutput().getText() == null) {
            log.warn("LLM 预处理返回空结果，将回退到规则分块");
            throw new RuntimeException("LLM 返回空结果");
        }

        String output = response.getResult().getOutput().getText();
        List<String> rawChunks = parseChunks(output);

        // 为每个语义块附加溯源信息
        List<SemanticChunk> result = new ArrayList<>();
        for (int i = 0; i < rawChunks.size(); i++) {
            result.add(new SemanticChunk(
                    rawChunks.get(i),
                    chapter != null ? chapter : "",
                    pageNumbers,
                    pageStr,
                    sourceFilename != null ? sourceFilename : "",
                    i
            ));
        }
        return result;
    }

    private List<String> callLlmAndParse(String text) {
        ChatModel chatModel = getChatModel();

        String promptText = properties.getPromptTemplate()
                .replace("{text}", text)
                .replace("{target}", String.valueOf(properties.getTargetChunkSize()));

        Prompt prompt = new Prompt(new UserMessage(promptText));
        ChatResponse response = chatModel.call(prompt);

        if (response == null || response.getResult() == null
                || response.getResult().getOutput() == null
                || response.getResult().getOutput().getText() == null) {
            log.warn("LLM 预处理返回空结果，将回退到规则分块");
            throw new RuntimeException("LLM 返回空结果");
        }

        String output = response.getResult().getOutput().getText();
        return parseChunks(output);
    }

    private List<String> parseChunks(String output) {
        if (output == null || output.isBlank()) return List.of();

        String delimiter = properties.getChunkDelimiter();
        String[] parts = output.split(delimiter);

        List<String> chunks = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.length() >= 20) {
                chunks.add(trimmed);
            }
        }

        if (chunks.isEmpty()) {
            String trimmed = output.trim();
            if (trimmed.length() >= 20) {
                chunks.add(trimmed);
            }
        }

        log.debug("LLM 输出解析：{} 个语义块", chunks.size());
        return chunks;
    }

    private ChatModel getChatModel() {
        Long modelId = properties.getModelId();
        if (modelId == null) throw new RuntimeException("未配置 knowledge.llm-preprocess.model-id");

        AiModel model = aiModelService.getModelById(modelId);
        if (model == null) throw new RuntimeException("预处理模型不存在: id=" + modelId);

        return chatModelFactory.getOrCreate(model);
    }

    private List<String> splitForLlm(String text, int maxLen) {
        List<String> segments = new ArrayList<>();
        String[] paragraphs = text.split("\n\n+");
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            if (current.length() + para.length() + 2 > maxLen) {
                if (current.length() > 0) {
                    segments.add(current.toString());
                    current = new StringBuilder();
                }
                if (para.length() > maxLen) {
                    for (int i = 0; i < para.length(); i += maxLen) {
                        segments.add(para.substring(i, Math.min(i + maxLen, para.length())));
                    }
                    continue;
                }
            }
            if (current.length() > 0) current.append("\n\n");
            current.append(para);
        }

        if (current.length() > 0) {
            segments.add(current.toString());
        }
        return segments;
    }

    // ===== 内部数据结构 =====

    /** 合并后的文档段（可能包含多个页） */
    private record MergedSegment(String text, String chapter, List<Integer> pageNumbers, String sourceFilename) {}

    /** 带溯源信息的语义块 */
    public record SemanticChunk(
            String content,        // 块文本内容
            String chapter,        // 所属章节
            List<Integer> pageNumbers, // 关联的页码列表
            String contextPages,   // 页码字符串 "1,2,3"
            String sourceFilename, // 源文件名
            int chunkIndex         // 块序号
    ) {}
}
