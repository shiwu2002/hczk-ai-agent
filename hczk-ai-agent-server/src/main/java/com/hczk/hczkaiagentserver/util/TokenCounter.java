package com.hczk.hczkaiagentserver.util;

/**
 * Token 计数工具类
 * 基于字符数估算 token 用量（适用于中文为主的场景）
 *
 * 估算规则：
 * - 中文字符：约 1.5 字符 = 1 token
 * - 英文/数字：约 4 字符 = 1 token
 * - 综合估算：约 2 字符 = 1 token（通用混合文本）
 *
 * 注意：这是估算值，实际 token 数以模型 API 返回的 usage 为准
 * 流式响应中无法获取精确 usage，因此使用估算作为计费依据
 */
public class TokenCounter {

    /** 综合估算比率：每 N 个字符约等于 1 个 token */
    private static final double CHARS_PER_TOKEN = 2.0;

    /**
     * 根据文本内容估算 token 数量
     * @param text 文本内容
     * @return 估算的 token 数量（向上取整，最小为 1）
     */
    public static long estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // 去除空白字符后按比率估算
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        return Math.max(1, Math.round(trimmed.length() / CHARS_PER_TOKEN));
    }

    /**
     * 计算输入 token 数（包含系统提示词 + 用户消息）
     * @param systemPrompt 系统提示词（可为 null）
     * @param userMessage 用户消息
     * @return 估算的输入 token 数量
     */
    public static long estimateInputTokens(String systemPrompt, String userMessage) {
        long tokens = estimateTokens(userMessage);
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            tokens += estimateTokens(systemPrompt);
        }
        return tokens;
    }
}
