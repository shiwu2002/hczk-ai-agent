package com.hczk.hczkaiagentserver.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OCR 服务配置（GLM-OCR）
 * 用于处理 PDF 扫描件、图片等无法通过文本提取获得有效内容的文档
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge.ocr")
public class OcrProperties {

    /** 是否启用 OCR（默认关闭） */
    private boolean enabled = false;

    /** OCR 模型 API Base URL（OpenAI 兼容协议） */
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";

    /** API Key */
    private String apiKey = "";

    /** 模型名称（GLM-OCR） */
    private String model = "glm-ocr";

    /** 调用超时（毫秒） */
    private long timeoutMs = 60000;

    /** 单次 OCR 最大重试次数 */
    private int maxRetries = 2;

    /**
     * 判断 PDF 是否为扫描件的阈值：
     * 当 PDFTextStripper 提取的文本字符数 / 页数 < 此值时，判定为扫描件，走 OCR
     * 默认 50 字符/页（正常 PDF 每页通常有数百字符以上）
     */
    private int scannedDetectCharsPerPage = 50;

    /** PDF 渲染为图片的 DPI（越高精度越高但越慢，默认 200） */
    private int renderDpi = 200;
}
