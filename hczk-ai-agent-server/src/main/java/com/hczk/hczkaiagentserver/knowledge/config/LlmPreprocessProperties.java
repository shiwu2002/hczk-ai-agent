package com.hczk.hczkaiagentserver.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 知识库入库 LLM 预处理配置
 * 用于在文档入库前通过 LLM 进行一轮总结与语义切割，提升检索质量
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge.llm-preprocess")
public class LlmPreprocessProperties {

    /** 是否启用 LLM 预处理（默认关闭，启用后非 QA 结构化文档会先经 LLM 总结切割） */
    private boolean enabled = false;

    /** 用于预处理的 AiModel ID（ai_models 表主键） */
    private Long modelId = 1L;

    /** 触发 LLM 预处理的最小文本长度（字符），短于此长度直接走规则分块 */
    private int minTextLength = 200;

    /** 单次送入 LLM 的最大文本长度（字符），超出则分段处理 */
    private int maxInputChars = 50000;

    /** LLM 产出的每个语义块的目标长度（字符），用于 prompt 引导 */
    private int targetChunkSize = 500;

    /** LLM 调用超时（毫秒） */
    private long timeoutMs = 120000;

    /** LLM 输出中块与块之间的分隔符 */
    private String chunkDelimiter = "\n\n===CHUNK===\n\n";

    /**
     * 预处理 Prompt 模板（{text} 为占位符，{target} 为目标块长度）
     * 要求模型：1) 理解文档主题 2) 按语义切分为独立完整的知识块 3) 每块保留关键事实，去除冗余
     */
    private String promptTemplate = """
            你是一个专业的知识库文档预处理助手。请阅读以下文档内容，进行总结与语义切割：

            要求：
            1. 理解文档的整体主题和结构
            2. 按语义将内容切分为多个独立、完整的知识块，每个块应能独立表达一个完整的知识点或事实
            3. 对冗长内容进行精炼总结，保留关键信息、数据、结论，去除口语化表达和重复内容
            4. 每个知识块目标长度约 {target} 字符，不要生硬截断句子
            5. 保持原文的专业术语和准确性，不要编造信息
            6. 各知识块之间用 ===CHUNK=== 分隔（前后各一个空行）
            7. 直接输出切割后的知识块，不要输出任何解释、前言、编号

            文档内容：
            ---
            {text}
            ---

            请输出切割后的知识块：
            """;
}
