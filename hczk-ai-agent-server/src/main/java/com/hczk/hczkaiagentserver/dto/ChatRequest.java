package com.hczk.hczkaiagentserver.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatRequest {
    /** 智能体 ID（通过智能体调用时使用，优先级高于 modelId） */
    private Long agentId;

    /** 模型 ID（直接指定模型调用时使用） */
    private Long modelId;

    /** 简单文本消息（内部调用格式） */
    private String message;

    /** 是否流式响应 */
    private Boolean stream = true;

    /** OpenAI 兼容格式的消息列表（外部 API 调用格式） */
    private List<OpenAiMessage> messages;

    /**
     * 获取实际要发送的消息内容
     * 优先使用 messages 列表中最后一条 user 消息，其次使用 message 字段
     */
    public String getEffectiveMessage() {
        if (messages != null && !messages.isEmpty()) {
            // 取最后一条 user 角色的消息
            for (int i = messages.size() - 1; i >= 0; i--) {
                OpenAiMessage msg = messages.get(i);
                if ("user".equals(msg.getRole())) {
                    return msg.getContent();
                }
            }
            // 没有 user 消息则取最后一条
            return messages.get(messages.size() - 1).getContent();
        }
        return message;
    }

    /**
     * OpenAI 兼容格式的消息对象
     */
    @Data
    public static class OpenAiMessage {
        private String role;
        private String content;
    }
}
