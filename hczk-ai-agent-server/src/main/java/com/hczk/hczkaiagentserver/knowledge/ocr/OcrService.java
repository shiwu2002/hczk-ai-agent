package com.hczk.hczkaiagentserver.knowledge.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hczk.hczkaiagentserver.knowledge.config.OcrProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * OCR 服务（GLM-OCR）
 * 将图片/PDF扫描页转为结构化文本，支持表格识别
 *
 * 调用方式：OpenAI 兼容的 chat/completions 协议，图片以 base64 data URL 传入
 */
@Slf4j
@Component
public class OcrService {

    private final OcrProperties properties;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OcrService(OcrProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(properties.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(properties.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 对图片字节进行 OCR，返回识别的文本
     *
     * @param imageBytes 图片字节数组（PNG/JPEG）
     * @return OCR 识别的文本内容（含表格的 Markdown 表示）
     */
    public String ocrImage(byte[] imageBytes) {
        if (!properties.isEnabled()) {
            log.warn("OCR 未启用，跳过图片识别");
            return "";
        }

        String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
        String dataUrl = "data:image/png;base64," + base64Image;

        String prompt = """
                请识别图片中的所有文字内容，要求：
                1. 完整保留原文，不要遗漏、不要编造
                2. 表格内容用 Markdown 表格格式输出（| 列1 | 列2 |）
                3. 保留段落结构，段落间用空行分隔
                4. 如果有页眉页脚，用 [页眉] 和 [页脚] 标记
                5. 直接输出识别结果，不要添加任何解释说明
                """;

        return callOcrApi(dataUrl, prompt);
    }

    /**
     * 调用 GLM-OCR API
     */
    private String callOcrApi(String dataUrl, String prompt) {
        String url = properties.getBaseUrl();
        if (!url.endsWith("/")) url += "/";
        url += "chat/completions";

        try {
            // 构建请求体（OpenAI 兼容格式，content 为多模态数组）
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", properties.getModel());
            requestBody.put("max_tokens", 4096);

            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode message = objectMapper.createObjectNode();
            message.put("role", "user");

            ArrayNode content = objectMapper.createArrayNode();

            // 文本指令
            ObjectNode textPart = objectMapper.createObjectNode();
            textPart.put("type", "text");
            textPart.put("text", prompt);
            content.add(textPart);

            // 图片
            ObjectNode imagePart = objectMapper.createObjectNode();
            imagePart.put("type", "image_url");
            ObjectNode imageUrl = objectMapper.createObjectNode();
            imageUrl.put("url", dataUrl);
            imagePart.set("image_url", imageUrl);
            content.add(imagePart);

            message.set("content", content);
            messages.add(message);
            requestBody.set("messages", messages);

            RequestBody body = RequestBody.create(
                    objectMapper.writeValueAsString(requestBody),
                    MediaType.parse("application/json"));

            Request.Builder rb = new Request.Builder()
                    .url(url)
                    .post(body)
                    .header("Content-Type", "application/json");

            if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                rb.header("Authorization", "Bearer " + properties.getApiKey());
            }

            // 重试
            Exception lastError = null;
            for (int attempt = 0; attempt <= properties.getMaxRetries(); attempt++) {
                try {
                    try (Response response = httpClient.newCall(rb.build()).execute()) {
                        if (!response.isSuccessful()) {
                            String respBody = response.body() != null ? response.body().string() : "";
                            throw new IOException("OCR API 返回 " + response.code() + ": " + respBody);
                        }
                        String respStr = response.body() != null ? response.body().string() : "";
                        return parseOcrResponse(respStr);
                    }
                } catch (Exception e) {
                    lastError = e;
                    if (attempt < properties.getMaxRetries()) {
                        log.warn("OCR 调用失败（尝试 {}/{}）: {}", attempt + 1, properties.getMaxRetries() + 1, e.getMessage());
                        Thread.sleep(1000L * (attempt + 1));
                    }
                }
            }
            log.error("OCR 调用最终失败: {}", lastError.getMessage());
            return "";
        } catch (Exception e) {
            log.error("OCR 调用异常: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 解析 OCR API 响应
     */
    private String parseOcrResponse(String respStr) throws Exception {
        JsonNode root = objectMapper.readTree(respStr);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode message = choices.get(0).path("message");
            JsonNode content = message.path("content");
            if (content.isTextual()) {
                return content.asText().trim();
            }
            // 部分 API 返回 content 为数组
            if (content.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode part : content) {
                    if ("text".equals(part.path("type").asText())) {
                        sb.append(part.path("text").asText());
                    }
                }
                return sb.toString().trim();
            }
        }
        log.warn("OCR 响应格式异常: {}", respStr.substring(0, Math.min(200, respStr.length())));
        return "";
    }
}
